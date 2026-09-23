#pragma once
#include <cstdint>
#include <cstring>
#include <ArduinoJson.h>

// Pure model + parser for the retained plant/schedule payload (no Arduino
// deps, unit-tested in test/test_schedule_model).

constexpr uint8_t SCHEDULE_MAX_ENTRIES = 8;   // must match ScheduleService.MAX_ENTRIES on the server
constexpr size_t  SCHEDULE_TZ_LEN      = 48;  // POSIX TZ string incl. terminator

struct ScheduleEntry {
    uint32_t secondOfDay;  // local time of day, 0..86340
    uint32_t durationS;
    uint8_t  daysMask;     // bit0 = Monday ... bit6 = Sunday
};

struct Schedule {
    uint32_t      version = 0;  // 0 = no schedule received yet
    char          tz[SCHEDULE_TZ_LEN] = {0};
    uint8_t       count = 0;
    ScheduleEntry entries[SCHEDULE_MAX_ENTRIES];
};

// "HH:MM" (exactly 5 chars, 24 h) -> seconds of day.
inline bool parseTimeOfDay(const char* hhmm, uint32_t& secondOfDay) {
    if (!hhmm || strlen(hhmm) != 5 || hhmm[2] != ':') return false;
    const int digitPositions[] = {0, 1, 3, 4};
    for (int pos : digitPositions) {
        if (hhmm[pos] < '0' || hhmm[pos] > '9') return false;
    }
    int hours   = (hhmm[0] - '0') * 10 + (hhmm[1] - '0');
    int minutes = (hhmm[3] - '0') * 10 + (hhmm[4] - '0');
    if (hours > 23 || minutes > 59) return false;
    secondOfDay = (uint32_t)(hours * 3600 + minutes * 60);
    return true;
}

// {"version":7,"tz":"CET-1CEST,M3.5.0,M10.5.0/3","entries":[{"t":"12:00","d":600,"w":21}]}
// All-or-nothing: on any invalid field returns false and leaves `out` untouched,
// so a broken message can never replace a working schedule.
inline bool parseSchedule(const char* json, uint32_t maxDurationS, Schedule& out) {
    JsonDocument doc;
    if (deserializeJson(doc, json)) return false;

    JsonVariantConst version = doc["version"];
    if (!version.is<uint32_t>() || version.as<uint32_t>() == 0) return false;

    const char* tz = doc["tz"];
    if (!tz || tz[0] == '\0' || strlen(tz) >= SCHEDULE_TZ_LEN) return false;

    JsonArrayConst entries = doc["entries"].as<JsonArrayConst>();
    if (entries.isNull() || entries.size() > SCHEDULE_MAX_ENTRIES) return false;

    Schedule parsed;
    parsed.version = version.as<uint32_t>();
    strncpy(parsed.tz, tz, SCHEDULE_TZ_LEN - 1);

    for (JsonVariantConst item : entries) {
        JsonObjectConst e = item.as<JsonObjectConst>();
        ScheduleEntry& entry = parsed.entries[parsed.count];

        const char* time = e["t"];
        if (!parseTimeOfDay(time, entry.secondOfDay)) return false;

        JsonVariantConst duration = e["d"];
        if (!duration.is<uint32_t>()) return false;
        entry.durationS = duration.as<uint32_t>();
        if (entry.durationS == 0 || entry.durationS > maxDurationS) return false;

        JsonVariantConst days = e["w"];
        if (!days.is<uint8_t>()) return false;
        entry.daysMask = days.as<uint8_t>();
        if (entry.daysMask == 0 || entry.daysMask > 127) return false;

        parsed.count++;
    }

    out = parsed;
    return true;
}
