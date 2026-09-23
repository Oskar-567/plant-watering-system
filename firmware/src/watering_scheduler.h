#pragma once
#include <Arduino.h>
#include "schedule_logic.h"

// Glue between the retained plant/schedule message, NVS, the clock and the
// pump. The decision logic itself lives in schedule_logic.h.
class WateringScheduler {
public:
    void begin();                    // load schedule + state from NVS (no clock/network needed)
    const char* timezone() const;    // TZ of the stored schedule, or DEFAULT_TZ
    void onScheduleMessage(const char* payload);
    void update();                   // call every loop()

private:
    Schedule      _schedule;
    ScheduleState _state;
    unsigned long _lastEvalMs = 0;

    void publishAck();
};

extern WateringScheduler wateringScheduler;
