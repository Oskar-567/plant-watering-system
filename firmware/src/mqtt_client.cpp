#include "mqtt_client.h"
#include "wifi_manager.h"
#include "../include/config.h"
#include <PubSubClient.h>
#include <WiFi.h>

static WiFiClient          wifiClient;
static PubSubClient        pubsub(wifiClient);
static MqttMessageCallback userCallback    = nullptr;
static MqttConnectCallback connectCallback = nullptr;

// Pending messages for publishQueued(). Sized for one pump stop report
// (flow + status + diag) plus a refused start; oldest is dropped when full.
static const uint8_t QUEUE_SIZE        = 6;
static const size_t  QUEUE_PAYLOAD_LEN = 128;
struct QueuedMessage {
    const char* topic;
    char        payload[QUEUE_PAYLOAD_LEN];
};
static QueuedMessage queue[QUEUE_SIZE];
static uint8_t       queueHead  = 0;
static uint8_t       queueCount = 0;

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

void MqttClient::setConnectCallback(MqttConnectCallback cb) {
    connectCallback = cb;
}

void MqttClient::begin() {
    pubsub.setServer(MQTT_BROKER, MQTT_PORT);
    pubsub.setCallback(onMessage);
    pubsub.setKeepAlive(MQTT_KEEPALIVE_SEC);
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
    flushQueue();
}

bool MqttClient::publish(const char* topic, const char* payload) {
    if (!pubsub.connected()) return false;
    if (!pubsub.publish(topic, payload)) {
        Serial.printf("MQTT publish FAILED [%s]: %s\n", topic, payload);
        return false;
    }
    Serial.printf("MQTT publish [%s]: %s\n", topic, payload);
    return true;
}

void MqttClient::publishQueued(const char* topic, const char* payload) {
    // Fast path only if nothing is waiting -- otherwise keep the order.
    if (queueCount == 0 && publish(topic, payload)) return;

    if (queueCount == QUEUE_SIZE) {
        Serial.printf("MQTT queue full, dropping [%s]: %s\n",
                      queue[queueHead].topic, queue[queueHead].payload);
        queueHead = (queueHead + 1) % QUEUE_SIZE;
        queueCount--;
    }
    QueuedMessage& slot = queue[(queueHead + queueCount) % QUEUE_SIZE];
    slot.topic = topic;
    strlcpy(slot.payload, payload, sizeof(slot.payload));
    queueCount++;
    Serial.printf("MQTT offline, queued [%s]: %s\n", topic, payload);
}

void MqttClient::flushQueue() {
    while (queueCount > 0) {
        QueuedMessage& msg = queue[queueHead];
        if (!publish(msg.topic, msg.payload)) return;  // retry next update()
        queueHead = (queueHead + 1) % QUEUE_SIZE;
        queueCount--;
    }
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
        if (connectCallback) connectCallback();
    } else {
        Serial.printf(" failed, rc=%d\n", pubsub.state());
    }
}

MqttClient mqttClient;
