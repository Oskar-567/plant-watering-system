#pragma once
#include <stdint.h>

// Pure ring-buffer logic for the remote debug log (no Arduino deps,
// unit-tested in test/test_log_buffer).
//
// LogStore holds only data, so on the ESP32 it can live in RTC_NOINIT_ATTR
// memory and survive a software reset, watchdog or panic. LogRingBuffer
// holds only logic and knows nothing about where the store lives, which is
// what makes it testable natively.

static const uint16_t LOG_LINE_LEN   = 96;
static const uint16_t LOG_LINE_COUNT = 40;

// Marks the store as written by this firmware. Uninitialised RTC memory
// after a power loss almost never matches it, and the index sanity check
// below catches the rest.
static const uint32_t LOG_STORE_MAGIC = 0x4C4F4731;  // "LOG1"

struct LogStore {
    uint32_t magic;
    uint16_t head;   // index the next line is written to
    uint16_t count;  // lines currently held (<= LOG_LINE_COUNT)
    char     lines[LOG_LINE_COUNT][LOG_LINE_LEN];
};

class LogRingBuffer {
public:
    explicit LogRingBuffer(LogStore& store) : _s(store) {}

    // Keeps the previous session's lines if the store is intact, discards
    // them otherwise. The index check guards against a reset that hit
    // mid-write and left the header half-updated.
    void begin() {
        if (_s.magic == LOG_STORE_MAGIC &&
            _s.head < LOG_LINE_COUNT &&
            _s.count <= LOG_LINE_COUNT) {
            _survived = _s.count > 0;
            return;
        }
        _s.magic  = LOG_STORE_MAGIC;
        _s.head   = 0;
        _s.count  = 0;
        _survived = false;
    }

    // True when begin() found lines written before the last reset.
    bool survivedReset() const { return _survived; }

    // Drops everything held, e.g. once the history has been published.
    // The store stays valid, so pushes after this keep being retained.
    void clear() {
        _s.head     = 0;
        _s.count    = 0;
        _survived   = false;
    }

    void push(const char* line) {
        char*    dst = _s.lines[_s.head];
        uint16_t n   = 0;
        while (line[n] != '\0' && n < LOG_LINE_LEN - 1) {
            dst[n] = line[n];
            n++;
        }
        dst[n] = '\0';

        _s.head = (_s.head + 1) % LOG_LINE_COUNT;
        if (_s.count < LOG_LINE_COUNT) _s.count++;
    }

    uint16_t count() const { return _s.count; }

    // 0 = oldest line held.
    const char* at(uint16_t i) const {
        uint16_t oldest = (_s.head + LOG_LINE_COUNT - _s.count) % LOG_LINE_COUNT;
        return _s.lines[(oldest + i) % LOG_LINE_COUNT];
    }

private:
    LogStore& _s;
    bool      _survived = false;
};

// --- Log levels ------------------------------------------------------------
// Ordered by severity: a line is emitted when its level is <= the active one,
// so LOG_LEVEL_ERROR is the quietest setting and LOG_LEVEL_DEBUG the loudest.

enum LogLevel : uint8_t {
    LOG_LEVEL_ERROR = 0,
    LOG_LEVEL_WARN  = 1,
    LOG_LEVEL_INFO  = 2,
    LOG_LEVEL_DEBUG = 3,
};

inline const char* logLevelName(LogLevel level) {
    switch (level) {
        case LOG_LEVEL_ERROR: return "error";
        case LOG_LEVEL_WARN:  return "warn";
        case LOG_LEVEL_INFO:  return "info";
        case LOG_LEVEL_DEBUG: return "debug";
    }
    return "info";
}

// Names are matched exactly, lower case, as sent on plant/debug/command.
// Returns false and leaves `out` untouched for anything else, so a typo in a
// hand-sent command can't silently change the level.
inline bool logLevelFromName(const char* name, LogLevel& out) {
    if (name == nullptr) return false;
    for (uint8_t i = 0; i <= LOG_LEVEL_DEBUG; i++) {
        LogLevel candidate = static_cast<LogLevel>(i);
        const char* known  = logLevelName(candidate);
        uint16_t    n      = 0;
        while (known[n] != '\0' && known[n] == name[n]) n++;
        if (known[n] == '\0' && name[n] == '\0') {
            out = candidate;
            return true;
        }
    }
    return false;
}

// --- plant/debug/command --------------------------------------------------
// Raised verbosity always expires on its own: this device runs off a solar
// charged 18650, and a forgotten "debug" would drain it through the radio.

static const uint16_t DEBUG_LEVEL_DEFAULT_MINUTES = 15;
static const uint16_t DEBUG_LEVEL_MAX_MINUTES     = 120;

namespace log_detail {

// Position just after `"<key>":` , or nullptr when the key is absent.
inline const char* valueAfterKey(const char* json, const char* key) {
    for (const char* p = json; *p != '\0'; p++) {
        if (*p != '"') continue;
        const char* q = p + 1;
        uint16_t    i = 0;
        while (key[i] != '\0' && q[i] == key[i]) i++;
        if (key[i] != '\0' || q[i] != '"') continue;
        const char* after = q + i + 1;
        while (*after == ' ') after++;
        if (*after != ':') continue;
        after++;
        while (*after == ' ') after++;
        return after;
    }
    return nullptr;
}

}  // namespace log_detail

// Parses {"level":"debug","minutes":30}. `minutes` is optional and capped.
// Returns false and leaves both outputs untouched when there is no valid
// level, so a malformed command never silently changes the verbosity.
inline bool parseDebugCommand(const char* json, LogLevel& level, uint16_t& minutes) {
    if (json == nullptr) return false;

    const char* value = log_detail::valueAfterKey(json, "level");
    if (value == nullptr || *value != '"') return false;
    value++;

    char     name[8];
    uint16_t n = 0;
    while (value[n] != '"' && value[n] != '\0' && n < sizeof(name) - 1) {
        name[n] = value[n];
        n++;
    }
    name[n] = '\0';

    LogLevel parsed = level;
    if (!logLevelFromName(name, parsed)) return false;

    uint16_t    parsedMinutes = DEBUG_LEVEL_DEFAULT_MINUTES;
    const char* minutesValue  = log_detail::valueAfterKey(json, "minutes");
    if (minutesValue != nullptr && *minutesValue >= '0' && *minutesValue <= '9') {
        uint32_t acc = 0;
        for (const char* d = minutesValue; *d >= '0' && *d <= '9'; d++) {
            acc = acc * 10 + static_cast<uint32_t>(*d - '0');
            if (acc > DEBUG_LEVEL_MAX_MINUTES) break;
        }
        parsedMinutes = acc > DEBUG_LEVEL_MAX_MINUTES
                            ? DEBUG_LEVEL_MAX_MINUTES
                            : static_cast<uint16_t>(acc);
    }

    level   = parsed;
    minutes = parsedMinutes;
    return true;
}
