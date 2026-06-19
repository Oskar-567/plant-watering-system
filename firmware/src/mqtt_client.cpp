#include "mqtt_client.h"
#include "wifi_manager.h"
#include "../include/config.h"
#include <PubSubClient.h>
#include <WiFi.h>

static WiFiClient          wifiClient;
static PubSubClient        pubsub(wifiClient);
static MqttMessageCallback userCallback = nullptr;

static void onMessage(char* topic, byte* payload, unsigned int length) {
    if (!userCallback) return;
    char msg[length + 1];
    memcpy(msg, payload, length);
    msg[length] = '\0';
    userCallback(topic, msg);
}

void MqttClient::setMessageCallback(MqttMessageCallback cb) {
    userCallback = cb;
}

void MqttClient::begin() {
    pubsub.setServer(MQTT_BROKER, MQTT_PORT);
    pubsub.setCallback(onMessage);
    reconnect();
}

void MqttClient::update() {
    if (!wifiManager.isConnected()) return;
    if (!pubsub.connected()) {
        static unsigned long lastReconnect = 0;
        if (millis() - lastReconnect >= 5000) {
            lastReconnect = millis();
            reconnect();
        }
        return;
    }
    pubsub.loop();
}

void MqttClient::publish(const char* topic, const char* payload) {
    if (!pubsub.connected()) return;
    pubsub.publish(topic, payload);
    Serial.printf("MQTT publish [%s]: %s\n", topic, payload);
}

bool MqttClient::isConnected() const {
    return pubsub.connected();
}

void MqttClient::reconnect() {
    if (!wifiManager.isConnected()) return;
    Serial.print("MQTT: connecting...");
    if (pubsub.connect(MQTT_CLIENT_ID, MQTT_USER, MQTT_PASSWORD)) {
        pubsub.subscribe("plant/pump/command");
        Serial.println(" connected");
    } else {
        Serial.printf(" failed, rc=%d\n", pubsub.state());
    }
}

MqttClient mqttClient;
