#pragma once
#include <cstdint>
#include "schedule_model.h"

// Pure "what should the scheduler do now" logic (no Arduino deps, unit-tested
// in test/test_schedule_logic). Callers pass local weekday/time separately
// from the epoch, so timezone handling stays on the device.

constexpr uint32_t SCHEDULE_SECONDS_PER_DAY = 86400UL;

// An occurrence computed up to this much later than the recorded one is the
// same occurrence: after the autumn DST switch "now - secondsSince" shifts by
// 1 h. Entries repeat at most daily, so 1 h can't merge two real occurrences.
constexpr uint32_t SCHEDULE_DST_TOLERANCE_S = 3600UL;

// Seconds since the most recent occurrence of `entry` at or before now
// (isoWeekday 1 = Monday ... 7 = Sunday, local secondOfDay). Looks back at
// most 7 days; -1 if the entry has no weekdays.
inline int32_t secondsSinceLastOccurrence(const ScheduleEntry& entry,
                                          uint8_t isoWeekday, uint32_t secondOfDay) {
    for (uint8_t daysBack = 0; daysBack <= 7; daysBack++) {
        uint8_t weekday = (uint8_t)(((isoWeekday - 1 + 7 - (daysBack % 7)) % 7) + 1);
        if (!(entry.daysMask & (1 << (weekday - 1)))) continue;
        if (daysBack == 0 && entry.secondOfDay > secondOfDay) continue;  // later today
        return (int32_t)(daysBack * SCHEDULE_SECONDS_PER_DAY + secondOfDay - entry.secondOfDay);
    }
    return -1;
}

// Persisted in NVS as raw bytes -- keep it trivially copyable.
struct ScheduleState {
    uint32_t acceptedAt = 0;                            // epoch this schedule version became active, 0 = not yet
    uint32_t lastHandled[SCHEDULE_MAX_ENTRIES] = {0};   // epoch of the last occurrence run/missed/refused
};

enum class ScheduleAction : uint8_t { None, Run, Missed };

struct ScheduleDecision {
    ScheduleAction action     = ScheduleAction::None;
    uint8_t        entryIndex = 0;
    uint32_t       occurrence = 0;  // epoch of the scheduled occurrence
};

// Rules:
//  - nothing before the schedule was accepted (acceptedAt == 0 -> no clock yet)
//  - occurrences before acceptedAt are ignored (saved at 12:10 -> 12:00 doesn't fire)
//  - each occurrence is handled once (lastHandled, with DST tolerance)
//  - late <= catchupWindowS -> Run, later -> Missed
// Returns the first actionable entry; the caller marks it handled and calls again.
inline ScheduleDecision evaluateSchedule(const Schedule& schedule, const ScheduleState& state,
                                         uint32_t nowEpoch, uint8_t isoWeekday,
                                         uint32_t secondOfDay, uint32_t catchupWindowS) {
    ScheduleDecision none;
    if (state.acceptedAt == 0) return none;

    for (uint8_t i = 0; i < schedule.count; i++) {
        int32_t since = secondsSinceLastOccurrence(schedule.entries[i], isoWeekday, secondOfDay);
        if (since < 0 || (uint32_t)since > nowEpoch) continue;

        uint32_t occurrence = nowEpoch - (uint32_t)since;
        if (occurrence < state.acceptedAt) continue;
        if (occurrence <= state.lastHandled[i] + SCHEDULE_DST_TOLERANCE_S) continue;

        ScheduleDecision decision;
        decision.action = ((uint32_t)since <= catchupWindowS) ? ScheduleAction::Run : ScheduleAction::Missed;
        decision.entryIndex = i;
        decision.occurrence = occurrence;
        return decision;
    }
    return none;
}
