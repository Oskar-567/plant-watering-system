#pragma once
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

// Pure JSON builders for the reports the server parses (no Arduino deps,
// unit-tested in test/test_report_format). Each returns false if the
// buffer was too small -- a truncated report is invalid JSON.

namespace report_detail {

inline void append(char* buf, size_t len, size_t& n, const char* fmt, ...) {
    if (n >= len) return;
    va_list args;
    va_start(args, fmt);
    int written = vsnprintf(buf + n, len - n, fmt, args);
    va_end(args);
    if (written > 0) n += static_cast<size_t>(written);
}

inline bool finish(char* buf, size_t len, size_t n) {
    if (len > 0) buf[len - 1] = '\0';
    return n < len;
}

}  // namespace report_detail

// plant/status: reason may be null; durationS 0 omits duration_s; a null or
// empty requestId omits id. ts is always written (0 = no clock).
inline bool formatPumpStatus(char* buf, size_t len, const char* pump, const char* trigger,
                             const char* reason, uint32_t durationS, const char* requestId,
                             uint32_t ts) {
    using report_detail::append;
    size_t n = 0;
    append(buf, len, n, "{\"pump\":\"%s\",\"trigger\":\"%s\"", pump, trigger);
    if (reason) append(buf, len, n, ",\"reason\":\"%s\"", reason);
    if (durationS > 0) append(buf, len, n, ",\"duration_s\":%lu", (unsigned long)durationS);
    if (requestId && requestId[0]) append(buf, len, n, ",\"id\":\"%s\"", requestId);
    append(buf, len, n, ",\"ts\":%lu}", (unsigned long)ts);
    return report_detail::finish(buf, len, n);
}

// plant/sensors/flow: once per run, right before the "off" status
inline bool formatFlow(char* buf, size_t len, float liters, const char* trigger, const char* requestId) {
    using report_detail::append;
    size_t n = 0;
    append(buf, len, n, "{\"liters\":%.3f,\"trigger\":\"%s\"", liters, trigger);
    if (requestId && requestId[0]) append(buf, len, n, ",\"id\":\"%s\"", requestId);
    append(buf, len, n, "}");
    return report_detail::finish(buf, len, n);
}

// plant/sensors/moisture: ts only with a valid clock
inline bool formatMoisture(char* buf, size_t len, const int* percents, uint8_t count, uint32_t ts) {
    using report_detail::append;
    size_t n = 0;
    append(buf, len, n, "{");
    for (uint8_t i = 0; i < count; i++) {
        append(buf, len, n, "%s\"sensor_%u\":%d", i ? "," : "", (unsigned)i, percents[i]);
    }
    if (ts > 0) append(buf, len, n, "%s\"ts\":%lu", count ? "," : "", (unsigned long)ts);
    append(buf, len, n, "}");
    return report_detail::finish(buf, len, n);
}

// plant/sensors/battery: ts only with a valid clock
inline bool formatBattery(char* buf, size_t len, float soc, float voltage, uint32_t ts) {
    using report_detail::append;
    size_t n = 0;
    append(buf, len, n, "{\"soc\":%.1f,\"voltage\":%.2f", soc, voltage);
    if (ts > 0) append(buf, len, n, ",\"ts\":%lu", (unsigned long)ts);
    append(buf, len, n, "}");
    return report_detail::finish(buf, len, n);
}
