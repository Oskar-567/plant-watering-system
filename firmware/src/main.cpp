#include <Arduino.h>
#include <Wire.h>
#include <esp_task_wdt.h>
#include <esp_system.h>
#include <esp_sleep.h>
#include <driver/gpio.h>
#include <WiFi.h>
#include "wifi_manager.h"
#include "mqtt_client.h"
#include "pump_controller.h"
#include "pump_command.h"
#include "flow_meter.h"
#include "moisture_sensors.h"
#include "battery_monitor.h"
#include "ota_handler.h"
#include "diagnostics.h"
#include "time_keeper.h"
#include "watering_scheduler.h"
#include "pump_request_handler.h"
#include "run_marker_store.h"
#include "publish_queue.h"
#include "wake_policy.h"
#include "log.h"
#include "report_format.h"
#include "../include/config.h"

static unsigned long lastSensorMs  = 0;
static unsigned long rebootAtMs    = 0;  // 0 = no reboot pending
static unsigned long lastBatteryMs = 0;
static unsigned long sleepAtMs     = 0;  // 0 = no test sleep pending
static uint16_t      sleepSeconds  = 0;

// Remote reboot. esp_restart() is a software reset, so the RTC log ring
// survives it -- which is what makes this usable for testing the post-mortem
// path, and for waking a wedged device without waiting out the watchdog.
static void requestReboot() {
    if (pumpController.isRunning()) {
        // A reset mid-run would abandon the flow count and the server would
        // never see the matching "off".
        LOG_WARN("Reboot refused: pump is running");
        return;
    }
    // Deferred, so the confirmation actually makes it onto the wire.
    logger.notice("Reboot requested, restarting in 1s");
    rebootAtMs = millis() + 1000;
}

// One deep sleep on request, for measuring the board's sleep current
// without USB. The wake is a normal boot, so OTA keeps working afterwards.
static void requestSleep(uint16_t seconds) {
    if (pumpController.isRunning()) {
        LOG_WARN("Sleep refused: pump is running");
        return;
    }
    // Deferred like the reboot, so the confirmation makes it onto the wire.
    logger.notice("Test sleep requested: %u s, sleeping in 1s", seconds);
    sleepSeconds = seconds;
    sleepAtMs    = millis() + 1000;
}

static void enterTestSleep() {
    if (pumpController.isRunning()) {  // a schedule run may have started meanwhile
        LOG_WARN("Sleep cancelled: pump is running");
        sleepAtMs = 0;
        return;
    }
    // GPIO 26 floats in deep sleep unless held; the module's pull-down is
    // the second line of defence. Released again in pumpController.begin().
    gpio_hold_en(static_cast<gpio_num_t>(RELAY_PIN));
    gpio_deep_sleep_hold_en();
    WiFi.disconnect(true);
    WiFi.mode(WIFI_OFF);
    esp_sleep_enable_timer_wakeup(static_cast<uint64_t>(sleepSeconds) * 1000000ULL);
    esp_deep_sleep_start();
}

// Awake window from plant/system/awake. Phase 2 never sleeps; the value is
// kept in RTC memory so the phase-3 wake cycle can honour it.
RTC_DATA_ATTR static uint32_t awakeUntil = 0;

static void onAwakeMessage(const char* payload) {
    uint32_t requested;
    if (!parseAwakeUntil(payload, requested)) {
        LOG_WARN("Awake: ignoring invalid payload: %s", payload);
        return;
    }
    awakeUntil = effectiveAwakeUntil(requested, timeKeeper.now(), AWAKE_MAX_S);
    if (awakeUntil) {
        LOG_INFO("Awake: window until %lu", (unsigned long)awakeUntil);
    } else {
        LOG_INFO("Awake: no window");
    }
}

static void onMqttMessage(const char* topic, const char* payload) {
    if (strcmp(topic, "plant/debug/command") == 0) {
        switch (parseDebugAction(payload)) {
            case DEBUG_ACTION_REBOOT:  requestReboot(); return;
            case DEBUG_ACTION_SLEEP:   requestSleep(parseSleepSeconds(payload)); return;
            case DEBUG_ACTION_UNKNOWN: LOG_WARN("Unknown action on plant/debug/command"); return;
            case DEBUG_ACTION_NONE:    logger.handleCommand(payload); return;
        }
        return;
    }
    if (strcmp(topic, "plant/system/awake") == 0) {
        onAwakeMessage(payload);
        return;
    }
    if (strcmp(topic, "plant/pump/request") == 0) {
        pumpRequestHandler.onRequestMessage(payload);
        return;
    }
    if (strcmp(topic, "plant/schedule") == 0) {
        wateringScheduler.onScheduleMessage(payload);
        return;
    }
    if (strcmp(topic, "plant/pump/command") != 0) return;

    // topic points into PubSubClient's buffer, which any publish below
    // overwrites -- never read it after dispatching.
    PumpCommand cmd = parsePumpCommand(payload, MAX_PUMP_RUNTIME_MS / 1000UL);
    switch (cmd.action) {
        case PumpAction::LegacyStart:
            LOG_DEBUG("MQTT: legacy start ignored, manual runs come from plant/pump/request");
            break;
        case PumpAction::Stop:
            pumpController.stop("command");
            break;
        case PumpAction::Invalid:
            LOG_WARN("MQTT: ignoring invalid pump command: %s", payload);
            mqttClient.publishQueued("plant/diag", "{\"event\":\"invalid_command\"}");
            break;
    }
}

// A marker that survived the reset: the run was cut off (brownout, watchdog,
// panic, power loss). Reported once, queued until MQTT is up. No retry.
static void reportInterruptedRun() {
    RunMarker marker;
    if (!runMarkerStore.load(marker)) return;
    uint32_t stopTs = interruptedStopTs(marker.startTs, marker.durationS, timeKeeper.now());
    const char* trigger = marker.trigger == RUN_MARKER_TRIGGER_SCHEDULE ? "schedule" : "manual";
    char payload[PUBLISH_QUEUE_PAYLOAD_LEN];
    formatPumpStatus(payload, sizeof(payload), "off", trigger, "interrupted", 0, marker.id, stopTs);
    mqttClient.publishQueued("plant/status", payload);
    LOG_WARN("Pump: run interrupted by a reset (%s, started %lu, %lus)", trigger,
             (unsigned long)marker.startTs, (unsigned long)marker.durationS);
    runMarkerStore.clear();
}

static void publishMoisture() {
    int percents[SENSOR_COUNT];
    for (uint8_t i = 0; i < SENSOR_COUNT; i++) percents[i] = moistureSensors.getPercent(i);
    char payload[256];
    formatMoisture(payload, sizeof(payload), percents, SENSOR_COUNT, timeKeeper.now());
    mqttClient.publish("plant/sensors/moisture", payload);
}

static void publishBattery() {
    char payload[64];
    formatBattery(payload, sizeof(payload), batteryMonitor.getSOC(), batteryMonitor.getVoltage(),
                  timeKeeper.now());
    mqttClient.publish("plant/sensors/battery", payload);

    if (batteryMonitor.getSOC() < BATTERY_LOW_THRESHOLD) {
        mqttClient.publish("plant/status", "{\"battery\":\"low\"}");
    }
}

void setup() {
    // Static frequency reduction -- the precompiled Arduino-ESP32 core for
    // classic ESP32 ships with CONFIG_PM_ENABLE off, so esp_pm/Automatic
    // Light Sleep isn't available with this build (see CLAUDE.md). This is
    // the fallback that's actually achievable without switching build
    // systems. Called before Serial.begin() so the UART starts up already
    // at the target frequency instead of needing a baud-rate resync after.
    setCpuFrequencyMhz(CPU_FREQ_MHZ);

    Serial.begin(115200);
    // Before anything that logs: setup() lines belong in the post-mortem
    // ring, and a boot loop is exactly what we want to be able to read back.
    logger.begin();
    LOG_INFO("CPU: running at %u MHz", getCpuFrequencyMhz());
    Wire.begin();
    LOG_INFO("I2C scan:");
    for (uint8_t addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0)
            LOG_INFO("  found device at 0x%02X", addr);
    }
    // Hardware first: relay off before the (slow) WiFi connect, and the fuel
    // gauge must be initialised before the MQTT connect callback reads it.
    pumpController.begin();
    flowMeter.begin();
    moistureSensors.begin();
    batteryMonitor.begin();
    wateringScheduler.begin();
    pumpRequestHandler.begin();
    reportInterruptedRun();
    diagnostics.begin();
    wifiManager.begin();
    timeKeeper.begin(wateringScheduler.timezone());  // after WiFi init -- see time_keeper.h
    mqttClient.setMessageCallback(onMqttMessage);
    mqttClient.setConnectCallback([]() {
        diagnostics.publishConnected();
        logger.publishHistory();
    });
    mqttClient.begin();
    otaHandler.begin();
    // Reboot if loop() stops running -- a hung ESP32 would otherwise keep
    // GPIO 26 driven and the pump on. Armed only after setup(), whose
    // blocking WiFi/MQTT connects are allowed to take a while. Timeout must
    // stay above the longest legit block in loop() (PubSubClient connect
    // waits up to 15 s for CONNACK). OTA feeds it from its progress callback.
    esp_task_wdt_init(WATCHDOG_TIMEOUT_S, true);
    esp_task_wdt_add(NULL);
    LOG_INFO("=== Plant watering system ready ===");
}

void loop() {
    esp_task_wdt_reset();
    logger.update();

    if (rebootAtMs != 0 && static_cast<long>(millis() - rebootAtMs) >= 0) {
        esp_restart();
    }
    if (sleepAtMs != 0 && static_cast<long>(millis() - sleepAtMs) >= 0) {
        enterTestSleep();
    }

    wifiManager.update();
    mqttClient.update();
    otaHandler.handle();
    pumpController.update();
    wateringScheduler.update();
    pumpRequestHandler.update();

    unsigned long now = millis();

    if (now - lastSensorMs >= SENSOR_INTERVAL_MS) {
        lastSensorMs = now;
        moistureSensors.read();
        publishMoisture();
    }

    if (now - lastBatteryMs >= BATTERY_INTERVAL_MS) {
        lastBatteryMs = now;
        batteryMonitor.read();
        publishBattery();
    }

    // Yields periodically so WiFi/MQTT background tasks stay serviced
    // promptly and the loop task doesn't hog its core (no light sleep to
    // enable here -- see CLAUDE.md on the esp_pm limitation).
    delay(20);
}
