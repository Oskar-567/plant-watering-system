#pragma once
#include <cstdint>
#include "pump_request.h"

// Pure part of the run marker (unit-tested in test/test_run_marker). The
// marker is written to NVS before the pump switches on and cleared when it
// stops; one that survives a reset means the run was cut off.

static const uint8_t RUN_MARKER_TRIGGER_MANUAL   = 0;
static const uint8_t RUN_MARKER_TRIGGER_SCHEDULE = 1;

struct RunMarker {
    uint8_t  trigger;                  // RUN_MARKER_TRIGGER_*
    char     id[REQUEST_ID_LEN + 1];   // request id, "" for schedule / legacy runs
    uint32_t startTs;                  // epoch seconds, 0 = no clock at start
    uint32_t durationS;
};

// When the interrupted run ended, as well as we can tell after the reset.
// now == 0: no clock (brownout / power loss). The firmware would have stopped
// by itself at the planned end, so that is the honest upper bound.
inline uint32_t interruptedStopTs(uint32_t startTs, uint32_t durationS, uint32_t now) {
    if (startTs == 0) return now;
    uint32_t plannedEnd = startTs + durationS;
    if (now == 0 || now > plannedEnd) return plannedEnd;
    return now < startTs ? startTs : now;
}
