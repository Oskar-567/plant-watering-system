#pragma once
#include <Arduino.h>

// Field diagnostics: why did the ESP32 last reboot, and how often / why did
// WiFi drop? Published to plant/diag on every MQTT (re)connect, so an outage
// (e.g. supply sag while pumping) shows up as soon as the link is back.
class Diagnostics {
public:
    void begin();             // call before wifiManager.begin()
    void publishConnected();  // call on every MQTT (re)connect

private:
    uint16_t _mqttConnects = 0;
};

extern Diagnostics diagnostics;
