#include "battery_monitor.h"
#include <Wire.h>
#include <SparkFun_MAX1704x_Fuel_Gauge_Arduino_Library.h>
#include "log.h"

static SFE_MAX1704X lipo(MAX1704X_MAX17048);

void BatteryMonitor::begin() {
    Wire.begin();
    if (!lipo.begin()) {
        LOG_ERROR("Battery: MAX17048 not found -- check wiring");
        return;
    }
    lipo.quickStart();
    LOG_INFO("Battery: MAX17048 ready");
}

void BatteryMonitor::read() {
    _soc     = lipo.getSOC();
    _voltage = lipo.getVoltage();
    LOG_DEBUG("Battery: %.1f%%, %.2fV", _soc, _voltage);
}

float BatteryMonitor::sampleVoltage() {
    _voltage = lipo.getVoltage();
    return _voltage;
}

float BatteryMonitor::getSOC() const     { return _soc; }
float BatteryMonitor::getVoltage() const { return _voltage; }

BatteryMonitor batteryMonitor;
