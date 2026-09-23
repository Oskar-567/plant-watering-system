#pragma once
#include <Arduino.h>

class BatteryMonitor {
public:
    void begin();
    void read();
    // Quick voltage-only read without Serial logging (for frequent checks
    // while the pump runs). Returns 0 on an I2C failure.
    float sampleVoltage();
    float getSOC() const;
    float getVoltage() const;

private:
    float _soc     = 0.0f;
    float _voltage = 0.0f;
};

extern BatteryMonitor batteryMonitor;
