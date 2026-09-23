#pragma once
#include <Arduino.h>
#include "schedule_logic.h"

constexpr size_t SCHEDULE_JSON_MAX_LEN = 1024;  // same as the MQTT buffer

// NVS persistence (namespace "schedule") of the last accepted schedule JSON
// and its execution state, so a reboot neither loses the schedule nor
// re-runs an occurrence that was already handled.
class ScheduleStore {
public:
    bool loadJson(char* buf, size_t len);  // false if nothing stored
    void saveJson(const char* json);
    void loadState(ScheduleState& state);  // resets state if nothing stored
    void saveState(const ScheduleState& state);
};
