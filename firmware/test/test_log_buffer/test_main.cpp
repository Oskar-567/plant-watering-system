#include <unity.h>
#include <stdio.h>
#include <string.h>
#include "../../src/log_buffer.h"

void setUp() {}
void tearDown() {}

// --- ring buffer: storing and reading back lines ---
void test_pushed_line_can_be_read_back() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();

    buf.push("hello");

    TEST_ASSERT_EQUAL_UINT16(1, buf.count());
    TEST_ASSERT_EQUAL_STRING("hello", buf.at(0));
}

void test_oldest_lines_are_dropped_when_full() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();

    // One more than the buffer holds -- line "0" must be gone.
    for (uint16_t i = 0; i < LOG_LINE_COUNT + 1; i++) {
        char line[8];
        snprintf(line, sizeof(line), "%u", i);
        buf.push(line);
    }

    TEST_ASSERT_EQUAL_UINT16(LOG_LINE_COUNT, buf.count());
    TEST_ASSERT_EQUAL_STRING("1", buf.at(0));
    TEST_ASSERT_EQUAL_STRING("40", buf.at(LOG_LINE_COUNT - 1));
}

void test_overlong_line_is_truncated_not_overflowed() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();

    char overlong[LOG_LINE_LEN + 50];
    memset(overlong, 'x', sizeof(overlong) - 1);
    overlong[sizeof(overlong) - 1] = '\0';

    buf.push(overlong);

    TEST_ASSERT_EQUAL_UINT16(LOG_LINE_LEN - 1, strlen(buf.at(0)));
}

// --- surviving a reset: the store lives in RTC memory on the ESP32, so it
// keeps its contents across a software reset, watchdog or panic, but holds
// garbage after a real power loss. begin() has to tell the two apart. ---
void test_fresh_store_starts_empty() {
    LogStore store{};            // zeroed: first boot ever
    LogRingBuffer buf(store);

    buf.begin();

    TEST_ASSERT_EQUAL_UINT16(0, buf.count());
    TEST_ASSERT_FALSE(buf.survivedReset());
}

void test_lines_survive_a_software_reset() {
    LogStore store{};
    LogRingBuffer first(store);
    first.begin();
    first.push("before the crash");

    // Reboot: same RTC memory, a new object over it.
    LogRingBuffer afterReset(store);
    afterReset.begin();

    TEST_ASSERT_TRUE(afterReset.survivedReset());
    TEST_ASSERT_EQUAL_UINT16(1, afterReset.count());
    TEST_ASSERT_EQUAL_STRING("before the crash", afterReset.at(0));
}

void test_garbage_after_power_loss_is_discarded() {
    LogStore store;
    memset(&store, 0xA5, sizeof(store));   // uninitialised RTC memory
    LogRingBuffer buf(store);

    buf.begin();

    TEST_ASSERT_FALSE(buf.survivedReset());
    TEST_ASSERT_EQUAL_UINT16(0, buf.count());
}

void test_valid_magic_with_impossible_indices_is_discarded() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();
    buf.push("a line");
    // Magic still intact, but a partial write left the indices corrupt.
    store.count = LOG_LINE_COUNT + 7;

    LogRingBuffer afterReset(store);
    afterReset.begin();

    TEST_ASSERT_FALSE(afterReset.survivedReset());
    TEST_ASSERT_EQUAL_UINT16(0, afterReset.count());
}

// --- clearing after the history has been published ---
void test_clear_empties_the_buffer_but_keeps_it_usable() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();
    buf.push("old line");

    buf.clear();
    TEST_ASSERT_EQUAL_UINT16(0, buf.count());

    buf.push("new line");
    TEST_ASSERT_EQUAL_UINT16(1, buf.count());
    TEST_ASSERT_EQUAL_STRING("new line", buf.at(0));
}

void test_cleared_buffer_is_not_reported_as_survived_after_reset() {
    LogStore store{};
    LogRingBuffer buf(store);
    buf.begin();
    buf.push("already published");
    buf.clear();

    LogRingBuffer afterReset(store);
    afterReset.begin();

    // Nothing unpublished left, so there is no history to send again.
    TEST_ASSERT_FALSE(afterReset.survivedReset());
}

// --- log level names, as sent on plant/debug/command ---
void test_known_level_names_are_parsed() {
    LogLevel level = LOG_LEVEL_INFO;
    TEST_ASSERT_TRUE(logLevelFromName("error", level));
    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_ERROR, level);
    TEST_ASSERT_TRUE(logLevelFromName("debug", level));
    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_DEBUG, level);
}

void test_unknown_level_name_is_rejected_and_leaves_level_untouched() {
    LogLevel level = LOG_LEVEL_WARN;

    TEST_ASSERT_FALSE(logLevelFromName("verbose", level));

    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_WARN, level);
}

void test_level_name_round_trips() {
    TEST_ASSERT_EQUAL_STRING("debug", logLevelName(LOG_LEVEL_DEBUG));
    TEST_ASSERT_EQUAL_STRING("info", logLevelName(LOG_LEVEL_INFO));
}

// --- parsing plant/debug/command ---
void test_command_sets_level_and_duration() {
    LogLevel level = LOG_LEVEL_INFO;
    uint16_t minutes = 0;

    TEST_ASSERT_TRUE(parseDebugCommand("{\"level\":\"debug\",\"minutes\":30}", level, minutes));

    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_DEBUG, level);
    TEST_ASSERT_EQUAL_UINT16(30, minutes);
}

void test_command_without_minutes_uses_the_default() {
    LogLevel level = LOG_LEVEL_INFO;
    uint16_t minutes = 0;

    TEST_ASSERT_TRUE(parseDebugCommand("{\"level\":\"warn\"}", level, minutes));

    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_WARN, level);
    TEST_ASSERT_EQUAL_UINT16(DEBUG_LEVEL_DEFAULT_MINUTES, minutes);
}

void test_command_with_unknown_level_is_rejected() {
    LogLevel level = LOG_LEVEL_INFO;
    uint16_t minutes = 7;

    TEST_ASSERT_FALSE(parseDebugCommand("{\"level\":\"verbose\"}", level, minutes));

    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_INFO, level);
    TEST_ASSERT_EQUAL_UINT16(7, minutes);
}

void test_command_without_level_key_is_rejected() {
    LogLevel level = LOG_LEVEL_INFO;
    uint16_t minutes = 7;

    TEST_ASSERT_FALSE(parseDebugCommand("{\"minutes\":30}", level, minutes));

    TEST_ASSERT_EQUAL_INT(LOG_LEVEL_INFO, level);
}

void test_excessive_duration_is_capped_to_protect_the_battery() {
    LogLevel level = LOG_LEVEL_INFO;
    uint16_t minutes = 0;

    TEST_ASSERT_TRUE(parseDebugCommand("{\"level\":\"debug\",\"minutes\":9999}", level, minutes));

    TEST_ASSERT_EQUAL_UINT16(DEBUG_LEVEL_MAX_MINUTES, minutes);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_pushed_line_can_be_read_back);
    RUN_TEST(test_oldest_lines_are_dropped_when_full);
    RUN_TEST(test_overlong_line_is_truncated_not_overflowed);
    RUN_TEST(test_fresh_store_starts_empty);
    RUN_TEST(test_lines_survive_a_software_reset);
    RUN_TEST(test_garbage_after_power_loss_is_discarded);
    RUN_TEST(test_valid_magic_with_impossible_indices_is_discarded);
    RUN_TEST(test_clear_empties_the_buffer_but_keeps_it_usable);
    RUN_TEST(test_cleared_buffer_is_not_reported_as_survived_after_reset);
    RUN_TEST(test_known_level_names_are_parsed);
    RUN_TEST(test_unknown_level_name_is_rejected_and_leaves_level_untouched);
    RUN_TEST(test_level_name_round_trips);
    RUN_TEST(test_command_sets_level_and_duration);
    RUN_TEST(test_command_without_minutes_uses_the_default);
    RUN_TEST(test_command_with_unknown_level_is_rejected);
    RUN_TEST(test_command_without_level_key_is_rejected);
    RUN_TEST(test_excessive_duration_is_capped_to_protect_the_battery);
    return UNITY_END();
}
