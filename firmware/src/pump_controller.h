#pragma once
#include <Arduino.h>
#include "pump_safety.h"
#include "../include/config.h"

enum class PumpTrigger : uint8_t { Manual, Schedule };

class PumpController {
public:
    void begin();
    // Runs the pump for durationS seconds, then stops by itself ("completed").
    // Returns false if refused: already running ("busy") or battery too low.
    bool start(uint32_t durationS, PumpTrigger trigger);
    // "command" for a stop command; the safety cutoffs pass their own reason.
    void stop(const char* reason);
    void update();
    bool isRunning() const;

private:
    bool          _running            = false;
    PumpTrigger   _trigger            = PumpTrigger::Manual;
    unsigned long _startMs            = 0;
    unsigned long _durationMs         = 0;
    unsigned long _lastFlowCheckMs    = 0;
    float         _litersAtLastCheck  = 0.0f;
    unsigned long _lastVoltageCheckMs = 0;
    float         _minVoltage         = 0.0f;  // lowest plausible reading this run, 0 = none
    LowVoltageDetector _lowVoltage{PUMP_MIN_RUN_VOLTAGE, PUMP_LOW_VOLTAGE_SAMPLES};

    void setRelay(bool on);
    void publishStatus(const char* pump, PumpTrigger trigger, const char* reason);
    void trackMinVoltage(float voltage);
};

extern PumpController pumpController;
