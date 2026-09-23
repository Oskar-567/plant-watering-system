#include <Arduino.h>
#include <Wire.h>
#include <esp_task_wdt.h>
#include "wifi_manager.h"
#include "mqtt_client.h"
#include "pump_controller.h"
#include "flow_meter.h"
#include "moisture_sensors.h"
#include "battery_monitor.h"
#include "ota_handler.h"
#include "diagnostics.h"
#include "../include/config.h"

static unsigned long lastSensorMs  = 0;
static unsigned long lastBatteryMs = 0;

static void onMqttMessage(const char* topic, const char* payload) {
    if (strcmp(topic, "plant/pump/command") != 0) return;
    if (strstr(payload, "start")) {
        pumpController.start();
    } else if (strstr(payload, "stop")) {
        pumpController.stop();
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
    Serial.printf("CPU: running at %u MHz\n", getCpuFrequencyMhz());
    Wire.begin();
    Serial.println("I2C scan:");
    for (uint8_t addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0)
            Serial.printf("  found device at 0x%02X\n", addr);
    }
    // Hardware first: relay off before the (slow) WiFi connect, and the fuel
    // gauge must be initialised before the MQTT connect callback reads it.
    pumpController.begin();
    flowMeter.begin();
    moistureSensors.begin();
    batteryMonitor.begin();
    diagnostics.begin();
    wifiManager.begin();
    mqttClient.setMessageCallback(onMqttMessage);
    mqttClient.setConnectCallback([]() { diagnostics.publishConnected(); });
    mqttClient.begin();
    otaHandler.begin();
    // Reboot if loop() stops running -- a hung ESP32 would otherwise keep
    // GPIO 26 driven and the pump on. Armed only after setup(), whose
    // blocking WiFi/MQTT connects are allowed to take a while. Timeout must
    // stay above the longest legit block in loop() (PubSubClient connect
    // waits up to 15 s for CONNACK). OTA feeds it from its progress callback.
    esp_task_wdt_init(WATCHDOG_TIMEOUT_S, true);
    esp_task_wdt_add(NULL);
    Serial.println("=== Plant watering system ready ===");
}

void loop() {
    esp_task_wdt_reset();
    wifiManager.update();
    mqttClient.update();
    otaHandler.handle();
    pumpController.update();

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
