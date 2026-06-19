#include "battery_monitor.h"
#include <Wire.h>
#include <SparkFun_MAX1704x_Fuel_Gauge_Arduino_Library.h>

static SFE_MAX1704X lipo(MAX1704X_MAX17048);

void BatteryMonitor::begin() {
    Wire.begin();
    if (!lipo.begin()) {
        Serial.println("Battery: MAX17048 not found -- check wiring");
        return;
    }
    lipo.quickStart();
    Serial.println("Battery: MAX17048 ready");
}

void BatteryMonitor::read() {
    _soc     = lipo.getSOC();
    _voltage = lipo.getVoltage();
    Serial.printf("Battery: %.1f%%, %.2fV\n", _soc, _voltage);
}

float BatteryMonitor::getSOC() const     { return _soc; }
float BatteryMonitor::getVoltage() const { return _voltage; }

BatteryMonitor batteryMonitor;
