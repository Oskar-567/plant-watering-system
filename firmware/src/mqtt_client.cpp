#include "mqtt_client.h"
#include "wifi_manager.h"
#include "../include/config.h"
#include <PubSubClient.h>
#include <WiFi.h>
#include "log.h"
#include "publish_queue.h"

static WiFiClient          wifiClient;
static PubSubClient        pubsub(wifiClient);
static MqttMessageCallback userCallback    = nullptr;
static MqttConnectCallback connectCallback = nullptr;

// Pending messages for publishQueued(), see publish_queue.h for sizing.
static PublishQueue queue;

// PubSubClient's default 256-byte buffer is too small for a full schedule
// (8 entries ~ 300 bytes incl. topic) -- larger messages are silently dropped.
static const uint16_t MQTT_BUFFER_SIZE = 1024;

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
    pubsub.setBufferSize(MQTT_BUFFER_SIZE);
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
        LOG_WARN("MQTT publish FAILED [%s]: %s", topic, payload);
        return false;
    }
    // Topic and size only: the payload is visible on its own topic, and
    // repeating it here just truncates against LOG_LINE_LEN. The two
    // cases below DO log it, because a message that never reached the
    // broker exists nowhere else.
    LOG_DEBUG("MQTT publish [%s] %u B", topic, (unsigned)strlen(payload));
    return true;
}

void MqttClient::publishQueued(const char* topic, const char* payload) {
    // Fast path only if nothing is waiting -- otherwise keep the order.
    if (queue.empty() && publish(topic, payload)) return;

    if (queue.full()) {
        LOG_WARN("MQTT queue full, dropping [%s]: %s", queue.front().topic, queue.front().payload);
    }
    queue.push(topic, payload);
    LOG_INFO("MQTT offline, queued [%s] %u B", topic, (unsigned)strlen(payload));
}

void MqttClient::flushQueue() {
    while (!queue.empty()) {
        const QueuedMessage& msg = queue.front();
        if (!publish(msg.topic, msg.payload)) return;  // retry next update()
        queue.pop();
    }
}

bool MqttClient::isConnected() const {
    return pubsub.connected();
}

void MqttClient::reconnect() {
    if (!wifiManager.isConnected()) return;
    LOG_INFO("MQTT: connecting");
    if (pubsub.connect(MQTT_CLIENT_ID, MQTT_USER, MQTT_PASSWORD)) {
        pubsub.subscribe("plant/pump/command");
        pubsub.subscribe("plant/schedule", 1);  // retained: delivered on every (re)connect
        pubsub.subscribe("plant/debug/command");
        LOG_INFO("MQTT: connected");
        if (connectCallback) connectCallback();
    } else {
        LOG_WARN("MQTT: connect failed, rc=%d", pubsub.state());
    }
}

MqttClient mqttClient;
