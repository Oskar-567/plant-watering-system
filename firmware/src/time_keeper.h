#pragma once
#include <Arduino.h>

// Wall clock via SNTP. The ESP32 keeps the time across software/watchdog
// resets, but not across brownout or power loss -- isValid() is false until
// the next successful NTP sync.
class TimeKeeper {
public:
    // Must be called AFTER wifiManager.begin(): starting SNTP before the
    // network stack is initialised asserts and boot-loops the ESP32.
    void begin(const char* posixTz);
    void setTimezone(const char* posixTz);
    bool isValid() const;
    uint32_t now() const;  // epoch seconds, 0 if !isValid()
    bool localNow(uint8_t& isoWeekday, uint32_t& secondOfDay) const;  // false if !isValid()
};

extern TimeKeeper timeKeeper;
