#pragma once
#include <Arduino.h>
#include "flow_math.h"

class FlowMeter {
public:
    void begin();
    void resetCount();
    float getLiters() const;

private:
    static void IRAM_ATTR onPulse();
    static volatile uint32_t _pulseCount;
    static volatile uint32_t _lastPulseUs;
};

extern FlowMeter flowMeter;
