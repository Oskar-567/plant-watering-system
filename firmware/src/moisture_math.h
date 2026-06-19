#pragma once

// Capacitive sensors: high ADC = dry, low ADC = wet.
// Returns 0-100 (percent moisture), clamped to [0, 100].
inline int rawToPercent(int raw, int dry, int wet) {
    if (dry == wet) return 0;
    int pct = (dry - raw) * 100 / (dry - wet);
    if (pct < 0)   pct = 0;
    if (pct > 100) pct = 100;
    return pct;
}
