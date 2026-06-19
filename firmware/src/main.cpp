#include <Arduino.h>
#include "wifi_manager.h"
#include "mqtt_client.h"
#include "pump_controller.h"
#include "flow_meter.h"
#include "moisture_sensors.h"
#include "battery_monitor.h"
#include "ota_handler.h"
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
    Serial.begin(115200);
    wifiManager.begin();
    mqttClient.setMessageCallback(onMqttMessage);
    mqttClient.begin();
    pumpController.begin();
    flowMeter.begin();
    moistureSensors.begin();
    batteryMonitor.begin();
    otaHandler.begin();
    Serial.println("=== Plant watering system ready ===");
}

void loop() {
    wifiManager.update();
    mqttClient.update();
    otaHandler.handle();

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
}
