#pragma once
#include <Arduino.h>

class BatteryMonitor {
public:
    void begin();
    void read();
    float getSOC() const;
    float getVoltage() const;

private:
    float _soc     = 0.0f;
    float _voltage = 0.0f;
};

extern BatteryMonitor batteryMonitor;
