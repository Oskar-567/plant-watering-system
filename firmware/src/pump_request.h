#pragma once
#include <cstdint>
#include <cstring>
#include <ArduinoJson.h>

// Pure parser and decision for the retained plant/pump/request (no Arduino
// deps, unit-tested in test/test_pump_request). The server publishes a manual
// run as a request the device executes at most once, on its next wake.

static const size_t REQUEST_ID_LEN = 36;  // server event UUID

struct PumpRequest {
    bool     present;                    // false = no request / cleared with {"id":null}
    char     id[REQUEST_ID_LEN + 1];
    uint32_t durationS;
    uint32_t expires;                    // epoch seconds (server clock)
};

// {"id":"<uuid>","duration_s":120,"expires":1790751720} -> present
// {"id":null} (or no id)                              -> cleared
// Anything else -> false, `out` untouched (a garbled message must not drop
// a valid pending request).
inline bool parsePumpRequest(const char* json, uint32_t maxDurationS, PumpRequest& out) {
    JsonDocument doc;
    if (deserializeJson(doc, json)) return false;

    JsonVariantConst id = doc["id"];
    if (id.isNull()) {
        out = PumpRequest{};
        return true;
    }
    if (!id.is<const char*>()) return false;
    const char* idText = id.as<const char*>();
    size_t idLen = strlen(idText);
    if (idLen == 0 || idLen > REQUEST_ID_LEN) return false;

    JsonVariantConst duration = doc["duration_s"];
    if (!duration.is<uint32_t>()) return false;
    uint32_t durationS = duration.as<uint32_t>();
    if (durationS == 0 || durationS > maxDurationS) return false;

    JsonVariantConst expires = doc["expires"];
    if (!expires.is<uint32_t>() || expires.as<uint32_t>() == 0) return false;

    PumpRequest parsed{};
    parsed.present = true;
    memcpy(parsed.id, idText, idLen + 1);
    parsed.durationS = durationS;
    parsed.expires = expires.as<uint32_t>();
    out = parsed;
    return true;
}

enum class RequestAction : uint8_t {
    None,    // nothing to do (no request, or this id was already handled)
    Wait,    // keep it: no valid clock yet to check the expiry
    Start,   // run it now
    Reject,  // report rejected with `reason`
};

struct RequestDecision {
    RequestAction action;
    const char*   reason;  // only for Reject
};

// now == 0: no valid clock. The battery gate stays in PumpController::start.
inline RequestDecision decidePumpRequest(const PumpRequest& request, const char* lastHandledId,
                                         uint32_t now, bool pumpRunning) {
    if (!request.present) return {RequestAction::None, nullptr};
    if (lastHandledId && strcmp(request.id, lastHandledId) == 0) return {RequestAction::None, nullptr};
    if (now == 0) return {RequestAction::Wait, nullptr};
    if (request.expires <= now) return {RequestAction::Reject, "expired"};
    if (pumpRunning) return {RequestAction::Reject, "busy"};
    return {RequestAction::Start, nullptr};
}
