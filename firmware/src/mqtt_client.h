#pragma once
#include <Arduino.h>

typedef void (*MqttMessageCallback)(const char* topic, const char* payload);
typedef void (*MqttConnectCallback)();

class MqttClient {
public:
    void setMessageCallback(MqttMessageCallback cb);
    // Called after every successful (re)connect + subscribe.
    void setConnectCallback(MqttConnectCallback cb);
    void begin();
    void update();
    // Fire-and-forget: dropped while disconnected (periodic sensor data).
    bool publish(const char* topic, const char* payload);
    // Held in a small RAM queue while disconnected and sent after the next
    // reconnect, in order (pump events the server must not miss).
    // `topic` must be a string literal / static -- only the pointer is kept.
    void publishQueued(const char* topic, const char* payload);
    bool isConnected() const;

private:
    void reconnect();
    void flushQueue();
};

extern MqttClient mqttClient;
