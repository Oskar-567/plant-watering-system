#pragma once
#include <cstdint>
#include <ArduinoJson.h>

// Pure awake-window logic (unit-tested in test/test_wake_policy). A retained
// plant/system/awake {"until":<epoch>} keeps the device awake for OTA and
// debugging once it sleeps (phase 3); capped so a forgotten window cannot
// drain the cell. Phase 2 parses and stores it only.

static const uint32_t AWAKE_MAX_S = 1800;

// {"until":<epoch>} -> true; {"until":0} closes the window. Anything else -> false, `until` untouched.
inline bool parseAwakeUntil(const char* json, uint32_t& until) {
    JsonDocument doc;
    if (deserializeJson(doc, json)) return false;
    JsonVariantConst value = doc["until"];
    if (!value.is<uint32_t>()) return false;
    until = value.as<uint32_t>();
    return true;
}

// 0 = no window: no valid clock, or `requested` is not in the future.
inline uint32_t effectiveAwakeUntil(uint32_t requested, uint32_t now, uint32_t maxS) {
    if (now == 0 || requested <= now) return 0;
    return requested - now > maxS ? now + maxS : requested;
}
