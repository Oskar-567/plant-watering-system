#pragma once
#include <cstdint>
#include <cstring>
#include <ArduinoJson.h>

// Pure parser for plant/pump/command payloads (no Arduino deps, unit-tested
// in test/test_pump_command).

// LegacyStart: the pre-deep-sleep start command. The server still sends it next
// to every plant/pump/request until phase 4; the firmware runs requests only and
// ignores it -- running both would water twice.
enum class PumpAction : uint8_t { Invalid, LegacyStart, Stop };

struct PumpCommand {
    PumpAction action;
    uint32_t   durationS;  // only meaningful for LegacyStart
};

// {"action":"start","duration_s":600} -> LegacyStart, 600 (1..maxDurationS, ignored by the firmware)
// {"action":"stop"}                   -> Stop
// Anything else -> Invalid. A start without a valid duration is rejected on
// purpose: the ESP32 must always know when to stop by itself instead of
// depending on a stop command arriving.
inline PumpCommand parsePumpCommand(const char* json, uint32_t maxDurationS) {
    const PumpCommand invalid{PumpAction::Invalid, 0};

    JsonDocument doc;
    if (deserializeJson(doc, json)) return invalid;

    const char* action = doc["action"];
    if (!action) return invalid;
    if (strcmp(action, "stop") == 0) return {PumpAction::Stop, 0};
    if (strcmp(action, "start") != 0) return invalid;

    JsonVariantConst duration = doc["duration_s"];
    if (!duration.is<uint32_t>()) return invalid;
    uint32_t durationS = duration.as<uint32_t>();
    if (durationS == 0 || durationS > maxDurationS) return invalid;
    return {PumpAction::LegacyStart, durationS};
}
