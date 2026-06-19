#pragma once
#include <Arduino.h>

typedef void (*MqttMessageCallback)(const char* topic, const char* payload);

class MqttClient {
public:
    void setMessageCallback(MqttMessageCallback cb);
    void begin();
    void update();
    void publish(const char* topic, const char* payload);
    bool isConnected() const;

private:
    void reconnect();
};

extern MqttClient mqttClient;
