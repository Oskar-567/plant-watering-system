#include <unity.h>
#include "../../src/run_marker.h"

void setUp() {}
void tearDown() {}

static const uint32_t START = 1790751000;

void test_reset_before_the_planned_end_uses_now() {
    // Watchdog/panic keep the clock: the run really ended at the reset
    TEST_ASSERT_EQUAL_UINT32(START + 40, interruptedStopTs(START, 120, START + 40));
}

void test_reboot_long_after_the_planned_end_caps_at_the_planned_end() {
    // The firmware would have stopped by itself at the planned end anyway
    TEST_ASSERT_EQUAL_UINT32(START + 120, interruptedStopTs(START, 120, START + 3600));
}

// Review Focus 5: brownout / power loss -- no clock after the reset
void test_no_clock_after_reset_uses_the_planned_end() {
    TEST_ASSERT_EQUAL_UINT32(START + 120, interruptedStopTs(START, 120, 0));
}

void test_run_started_without_clock_reports_now() {
    // No start time to anchor on: now (0 = server falls back to receive time)
    TEST_ASSERT_EQUAL_UINT32(1790760000, interruptedStopTs(0, 120, 1790760000));
    TEST_ASSERT_EQUAL_UINT32(0, interruptedStopTs(0, 120, 0));
}

void test_clock_behind_the_start_never_reports_before_the_start() {
    TEST_ASSERT_EQUAL_UINT32(START, interruptedStopTs(START, 120, START - 500));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_reset_before_the_planned_end_uses_now);
    RUN_TEST(test_reboot_long_after_the_planned_end_caps_at_the_planned_end);
    RUN_TEST(test_no_clock_after_reset_uses_the_planned_end);
    RUN_TEST(test_run_started_without_clock_reports_now);
    RUN_TEST(test_clock_behind_the_start_never_reports_before_the_start);
    return UNITY_END();
}
