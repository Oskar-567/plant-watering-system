#include "flow_meter.h"
#include "../include/config.h"

volatile uint32_t FlowMeter::_pulseCount  = 0;
volatile uint32_t FlowMeter::_lastPulseUs = 0;

// Debounce: the pump motor running right next to the sensor induces
// electrical noise on the signal line, which the ESP32's weak internal
// pull-up doesn't filter -- without this, spurious extra RISING edges
// overcount pulses (observed ~40x pre-debounce on the bench, ~16x still
// getting through at 2ms under real installed/loaded conditions --
// raised to 20ms on 2026-07-27). FLOW_DEBOUNCE_US must stay below the
// real pulse interval at nominal flow (~1 L/min = 7.5 Hz = ~133ms) but
// as high as tolerable to reject EMI bursts; it's well under the sensor's
// rated max (30 L/min = ~225 Hz = ~4.4ms min interval), so there's no
// risk of it clipping legitimate fast pulses at this pump's flow rate.
void IRAM_ATTR FlowMeter::onPulse() {
    uint32_t now = micros();
    if (now - _lastPulseUs < FLOW_DEBOUNCE_US) return;
    _lastPulseUs = now;
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
