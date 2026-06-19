#pragma once
#include <stdint.h>

// Returns liters from pulse count. Returns 0 if pulsesPerLiter is 0.
inline float pulsesToLiters(uint32_t pulses, uint16_t pulsesPerLiter) {
    if (pulsesPerLiter == 0) return 0.0f;
    return static_cast<float>(pulses) / static_cast<float>(pulsesPerLiter);
}
