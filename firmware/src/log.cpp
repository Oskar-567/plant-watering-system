#include "log.h"
#include "mqtt_client.h"
#include <stdarg.h>

// RTC slow memory: kept across a software reset, watchdog and panic, and
// deliberately NOT zeroed at boot -- that is what makes a post-mortem log
// possible. It holds garbage after a real power loss, which LogRingBuffer
// detects via the magic value. Chosen over NVS because every line would
// otherwise be a flash write, and this device logs continuously.
RTC_NOINIT_ATTR static LogStore logStore;
static LogRingBuffer ring(logStore);

void Logger::begin() {
    ring.begin();
    _level      = LOG_LEVEL_INFO;
    _revertAtMs = 0;

    if (ring.survivedReset()) {
        Serial.printf("Log: %u lines survived the last reset, will publish on connect\n",
                      ring.count());
    }
}

void Logger::emit(LogLevel level, bool force, const char* fmt, va_list args) {
    // Re-entrant call: we are already inside a publish, and mqtt_client logs
    // its own traffic. Writing here would push into the ring buffer that
    // publishHistory() is iterating, overwriting the very lines being sent --
    // the post-mortem log would destroy itself while delivering. Suppressing
    // all three sinks, not just the MQTT one, is what makes that safe.
    if (_publishing) return;

    char message[LOG_LINE_LEN];
    vsnprintf(message, sizeof(message), fmt, args);

    // Stamp once, so a replayed history line reads exactly like it did live.
    // Plain text rather than JSON: a log line may contain quotes or
    // backslashes, and escaping them is a bug source the debug path does not
    // need. Uptime is used instead of a wall clock -- there is no RTC yet.
    char stamped[LOG_LINE_LEN];
    snprintf(stamped, sizeof(stamped), "%lu %s %s",
             millis() / 1000UL, logLevelName(level), message);

    Serial.println(stamped);
    ring.push(stamped);

    if (force || level <= _level) publishLine(stamped);
}

void Logger::line(LogLevel level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    emit(level, false, fmt, args);
    va_end(args);
}

void Logger::notice(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    emit(LOG_LEVEL_INFO, true, fmt, args);
    va_end(args);
}

void Logger::publishLine(const char* text) {
    if (!mqttClient.isConnected()) return;

    _publishing = true;
    mqttClient.publish("plant/log", text);
    _publishing = false;
}

void Logger::publishHistory() {
    if (!ring.survivedReset()) return;

    _publishing = true;
    uint16_t held = ring.count();
    for (uint16_t i = 0; i < held; i++) {
        mqttClient.publish("plant/log/history", ring.at(i));
    }
    _publishing = false;

    ring.clear();
    Serial.printf("Log: published %u lines from before the last reset\n", held);
}

bool Logger::handleCommand(const char* payload) {
    LogLevel requested = _level;
    uint16_t minutes   = 0;

    if (!parseDebugCommand(payload, requested, minutes)) {
        LOG_WARN("Log: ignored malformed debug command");
        return false;
    }

    _level = requested;
    // info is the resting level, so it never expires; anything louder does,
    // because the radio time would otherwise drain a solar-charged cell.
    _revertAtMs = (requested == LOG_LEVEL_INFO)
                      ? 0
                      : millis() + static_cast<unsigned long>(minutes) * 60000UL;

    notice("Log: level %s for %u min", logLevelName(requested), minutes);
    return true;
}

void Logger::update() {
    if (_revertAtMs == 0) return;
    // Signed comparison so this still works across the millis() rollover.
    if (static_cast<long>(millis() - _revertAtMs) < 0) return;

    _revertAtMs = 0;
    _level      = LOG_LEVEL_INFO;
    notice("Log: raised level expired, back to info");
}

Logger logger;
