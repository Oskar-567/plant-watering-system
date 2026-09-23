#include <Arduino.h>
#include <Wire.h>
#include <esp_task_wdt.h>
#include <esp_system.h>
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
#include "log.h"
#include "../include/config.h"

static unsigned long lastSensorMs  = 0;
static unsigned long rebootAtMs    = 0;  // 0 = no reboot pending
static unsigned long lastBatteryMs = 0;

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

static void onMqttMessage(const char* topic, const char* payload) {
    if (strcmp(topic, "plant/debug/command") == 0) {
        switch (parseDebugAction(payload)) {
            case DEBUG_ACTION_REBOOT:  requestReboot(); return;
            case DEBUG_ACTION_UNKNOWN: LOG_WARN("Unknown action on plant/debug/command"); return;
            case DEBUG_ACTION_NONE:    logger.handleCommand(payload); return;
        }
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
        case PumpAction::Start:
            pumpController.start(cmd.durationS, PumpTrigger::Manual);
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

static void publishMoisture() {
    char payload[256];
    int pos = snprintf(payload, sizeof(payload), "{");
    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
        pos += snprintf(payload + pos, sizeof(payload) - pos,
                        "\"sensor_%u\":%d%s",
                        i, moistureSensors.getPercent(i),
                        (i < SENSOR_COUNT - 1) ? "," : "");
    }
    snprintf(payload + pos, sizeof(payload) - pos, "}");
    mqttClient.publish("plant/sensors/moisture", payload);
}

static void publishBattery() {
    char payload[64];
    snprintf(payload, sizeof(payload),
             "{\"soc\":%.1f,\"voltage\":%.2f}",
             batteryMonitor.getSOC(), batteryMonitor.getVoltage());
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

    wifiManager.update();
    mqttClient.update();
    otaHandler.handle();
    pumpController.update();
    wateringScheduler.update();

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
