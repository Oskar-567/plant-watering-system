#include "time_keeper.h"
#include "log.h"
#include "../include/config.h"
#include <time.h>

// Anything before 2025-01-01 means the clock was never set since power-on.
static const time_t MIN_VALID_EPOCH = 1735689600;

void TimeKeeper::begin(const char* posixTz) {
    configTzTime(posixTz, NTP_SERVER);
    LOG_INFO("Time: SNTP started (%s), TZ=%s", NTP_SERVER, posixTz);
}

void TimeKeeper::setTimezone(const char* posixTz) {
    setenv("TZ", posixTz, 1);
    tzset();
    LOG_INFO("Time: TZ=%s", posixTz);
}

bool TimeKeeper::isValid() const {
    return time(nullptr) >= MIN_VALID_EPOCH;
}

uint32_t TimeKeeper::now() const {
    return isValid() ? (uint32_t)time(nullptr) : 0;
}

bool TimeKeeper::localNow(uint8_t& isoWeekday, uint32_t& secondOfDay) const {
    if (!isValid()) return false;
    time_t t = time(nullptr);
    struct tm local;
    localtime_r(&t, &local);
    isoWeekday  = local.tm_wday == 0 ? 7 : (uint8_t)local.tm_wday;
    secondOfDay = (uint32_t)(local.tm_hour * 3600 + local.tm_min * 60 + local.tm_sec);
    return true;
}

TimeKeeper timeKeeper;
