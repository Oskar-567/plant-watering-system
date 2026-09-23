#pragma once
#include <stdint.h>

// Pure battery-protection logic for the pump (no Arduino deps, unit-tested
// in test/test_pump_safety).

// A 1S Li-ion cell can't really sit outside this range; the MAX17048
// library returns 0 V on an I2C failure, so anything outside is treated
// as "no reading" rather than "empty battery".
inline bool isPlausibleCellVoltage(float voltage) {
    return voltage >= 2.5f && voltage <= 4.5f;
}

// Start gate. An implausible reading (gauge missing / I2C error) allows the
// start -- a broken gauge must not stop watering for good; the runtime
// cutoffs (max runtime, flow stall, link loss) still apply.
inline bool batteryAllowsPumpStart(float voltage, float soc,
                                   float minVoltage, float minSoc) {
    if (!isPlausibleCellVoltage(voltage)) return true;
    return voltage >= minVoltage && soc >= minSoc;
}

// Trips once the voltage under pump load has been below the threshold for
// `requiredSamples` consecutive readings -- ignores the short dip of the
// motor inrush. Invalid readings neither count nor reset the streak.
class LowVoltageDetector {
public:
    LowVoltageDetector(float threshold, uint8_t requiredSamples)
        : _threshold(threshold), _required(requiredSamples) {}

    void reset() { _count = 0; }

    bool addSample(float voltage) {
        if (!isPlausibleCellVoltage(voltage)) return false;
        if (voltage >= _threshold) {
            _count = 0;
            return false;
        }
        if (_count < _required) _count++;
        return _count >= _required;
    }

private:
    float   _threshold;
    uint8_t _required;
    uint8_t _count = 0;
};
