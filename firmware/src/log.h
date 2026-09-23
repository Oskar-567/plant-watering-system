#pragma once
#include <Arduino.h>
#include "log_buffer.h"

// Remote debug log. Every line goes to three places:
//   1. Serial            -- unchanged, for the rare occasion there is a cable
//   2. the RTC ring      -- survives a software reset, watchdog or panic
//   3. plant/log (MQTT)  -- only when the line's level is active and the
//                           link is up
//
// The interesting failures on this device (brownout while pumping, WiFi
// collapsing under load) happen with the link already down, so the live
// stream alone would show nothing. That is what the ring is for: on the
// next successful connect, the lines from before the reset go out on
// plant/log/history.
class Logger {
public:
    void begin();   // call right after Serial.begin()
    void update();  // call from loop(): expires a raised level

    void line(LogLevel level, const char* fmt, ...);

    // plant/debug/command -- {"level":"debug","minutes":30}
    bool handleCommand(const char* payload);

    // Call on every MQTT (re)connect; sends and clears a surviving log.
    void publishHistory();

    LogLevel level() const { return _level; }

private:
    void publishLine(const char* text);

    LogLevel      _level      = LOG_LEVEL_INFO;
    unsigned long _revertAtMs = 0;
    bool          _publishing = false;  // guards against logging while logging
};

extern Logger logger;

#define LOG_ERROR(...) logger.line(LOG_LEVEL_ERROR, __VA_ARGS__)
#define LOG_WARN(...)  logger.line(LOG_LEVEL_WARN,  __VA_ARGS__)
#define LOG_INFO(...)  logger.line(LOG_LEVEL_INFO,  __VA_ARGS__)
#define LOG_DEBUG(...) logger.line(LOG_LEVEL_DEBUG, __VA_ARGS__)
