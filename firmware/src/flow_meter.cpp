#include "flow_meter.h"
#include "../include/config.h"

volatile uint32_t FlowMeter::_pulseCount = 0;

void IRAM_ATTR FlowMeter::onPulse() {
    _pulseCount++;
}

void FlowMeter::begin() {
    pinMode(FLOW_METER_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(FLOW_METER_PIN), onPulse, RISING);
}

void FlowMeter::resetCount() {
    noInterrupts();
    _pulseCount = 0;
    interrupts();
}

float FlowMeter::getLiters() const {
    noInterrupts();
    uint32_t count = _pulseCount;
    interrupts();
    return pulsesToLiters(count, PULSES_PER_LITER);
}

FlowMeter flowMeter;
