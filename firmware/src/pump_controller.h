#pragma once
#include <Arduino.h>
#include "pump_safety.h"
#include "../include/config.h"

class PumpController {
public:
    void begin();
    void start();
    void stop();
    void update();
    bool isRunning() const;

private:
    bool          _running          = false;
    unsigned long _startMs          = 0;
    unsigned long _lastFlowCheckMs  = 0;
    float         _litersAtLastCheck = 0.0f;
    unsigned long _lastVoltageCheckMs = 0;
    float         _minVoltage       = 0.0f;  // lowest plausible reading this run, 0 = none
    LowVoltageDetector _lowVoltage{PUMP_MIN_RUN_VOLTAGE, PUMP_LOW_VOLTAGE_SAMPLES};

    void setRelay(bool on);
    void stopWithReason(const char* reason);
    void trackMinVoltage(float voltage);
};

extern PumpController pumpController;
