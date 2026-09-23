#include <unity.h>
#include "../../src/schedule_logic.h"

void setUp() {}
void tearDown() {}

// Arbitrary epoch -- weekday and local time are passed separately.
static const uint32_t NOW    = 2000000000UL;
static const uint32_t DAY    = 86400UL;
static const uint32_t NOON   = 12 * 3600UL;
static const uint32_t WINDOW = 1800UL;
static const uint8_t  MON = 1, WED = 3;

static Schedule scheduleWith(uint32_t secondOfDay, uint8_t daysMask) {
    Schedule s;
    s.version = 1;
    strcpy(s.tz, "UTC0");
    s.count = 1;
    s.entries[0] = {secondOfDay, 600, daysMask};
    return s;
}

static ScheduleState acceptedLongAgo() {
    ScheduleState st;
    st.acceptedAt = NOW - 30 * DAY;
    return st;
}

static void assertAction(ScheduleAction expected, const ScheduleDecision& d) {
    TEST_ASSERT_EQUAL_INT((int)expected, (int)d.action);
}

// --- secondsSinceLastOccurrence ---
void test_since_today_passed() {
    ScheduleEntry e{NOON, 600, 0x7F};
    TEST_ASSERT_EQUAL_INT32(1200, secondsSinceLastOccurrence(e, WED, NOON + 1200));
}
void test_since_later_today_uses_yesterday() {
    ScheduleEntry e{NOON, 600, 0x7F};
    TEST_ASSERT_EQUAL_INT32(DAY - 3600, secondsSinceLastOccurrence(e, WED, NOON - 3600));
}
void test_since_same_weekday_later_today_goes_back_a_week() {
    ScheduleEntry e{NOON, 600, 0x01};  // Monday only
    TEST_ASSERT_EQUAL_INT32(7 * DAY - 3600, secondsSinceLastOccurrence(e, MON, NOON - 3600));
}
void test_since_wraps_sunday_to_monday() {
    ScheduleEntry e{23 * 3600UL, 600, 0x40};  // Sunday 23:00
    TEST_ASSERT_EQUAL_INT32(4200, secondsSinceLastOccurrence(e, MON, 600));  // Monday 00:10
}
void test_since_no_days_returns_minus_one() {
    ScheduleEntry e{NOON, 600, 0x00};
    TEST_ASSERT_EQUAL_INT32(-1, secondsSinceLastOccurrence(e, WED, NOON));
}

// --- evaluateSchedule ---
void test_nothing_until_accepted() {
    ScheduleState st;  // acceptedAt = 0
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON, WINDOW));
}
void test_run_exactly_on_time() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - DAY;  // yesterday handled
    ScheduleDecision d = evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON, WINDOW);
    assertAction(ScheduleAction::Run, d);
    TEST_ASSERT_EQUAL_UINT8(0, d.entryIndex);
    TEST_ASSERT_EQUAL_UINT32(NOW, d.occurrence);
}
void test_run_when_late_within_window() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 1200 - DAY;
    ScheduleDecision d = evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + 1200, WINDOW);
    assertAction(ScheduleAction::Run, d);
    TEST_ASSERT_EQUAL_UINT32(NOW - 1200, d.occurrence);
}
void test_run_at_window_edge() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - WINDOW - DAY;
    assertAction(ScheduleAction::Run, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + WINDOW, WINDOW));
}
void test_missed_when_later_than_window() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 1860 - DAY;
    ScheduleDecision d = evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + 1860, WINDOW);
    assertAction(ScheduleAction::Missed, d);
    TEST_ASSERT_EQUAL_UINT32(NOW - 1860, d.occurrence);
}
void test_nothing_before_todays_time() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - (DAY - 3600);  // yesterday's noon, seen from 11:00
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON - 3600, WINDOW));
}
void test_same_occurrence_not_handled_twice() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 300;
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + 300, WINDOW));
}
void test_dst_shifted_occurrence_counts_as_handled() {
    // After the autumn clock change the same occurrence computes 1 h later.
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 300 - 3600;
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + 300, WINDOW));
}
void test_occurrence_before_acceptance_is_ignored() {
    ScheduleState st;
    st.acceptedAt = NOW - 300;  // schedule arrived at 12:10, now 12:15, entry 12:00
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x7F), st, NOW, WED, NOON + 900, WINDOW));
}
void test_weekday_mask_reports_missed_monday_on_wednesday() {
    ScheduleState st = acceptedLongAgo();
    ScheduleDecision d = evaluateSchedule(scheduleWith(NOON, 0x01), st, NOW, WED, NOON, WINDOW);
    assertAction(ScheduleAction::Missed, d);
    TEST_ASSERT_EQUAL_UINT32(NOW - 2 * DAY, d.occurrence);
}
void test_weekday_mask_nothing_when_monday_handled() {
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 2 * DAY;
    assertAction(ScheduleAction::None, evaluateSchedule(scheduleWith(NOON, 0x01), st, NOW, WED, NOON, WINDOW));
}
void test_second_entry_due_when_first_handled() {
    Schedule s = scheduleWith(8 * 3600UL, 0x7F);
    s.entries[1] = {NOON, 300, 0x7F};
    s.count = 2;
    ScheduleState st = acceptedLongAgo();
    st.lastHandled[0] = NOW - 4 * 3600;  // today 08:00
    st.lastHandled[1] = NOW - DAY;       // yesterday 12:00
    ScheduleDecision d = evaluateSchedule(s, st, NOW, WED, NOON, WINDOW);
    assertAction(ScheduleAction::Run, d);
    TEST_ASSERT_EQUAL_UINT8(1, d.entryIndex);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_since_today_passed);
    RUN_TEST(test_since_later_today_uses_yesterday);
    RUN_TEST(test_since_same_weekday_later_today_goes_back_a_week);
    RUN_TEST(test_since_wraps_sunday_to_monday);
    RUN_TEST(test_since_no_days_returns_minus_one);
    RUN_TEST(test_nothing_until_accepted);
    RUN_TEST(test_run_exactly_on_time);
    RUN_TEST(test_run_when_late_within_window);
    RUN_TEST(test_run_at_window_edge);
    RUN_TEST(test_missed_when_later_than_window);
    RUN_TEST(test_nothing_before_todays_time);
    RUN_TEST(test_same_occurrence_not_handled_twice);
    RUN_TEST(test_dst_shifted_occurrence_counts_as_handled);
    RUN_TEST(test_occurrence_before_acceptance_is_ignored);
    RUN_TEST(test_weekday_mask_reports_missed_monday_on_wednesday);
    RUN_TEST(test_weekday_mask_nothing_when_monday_handled);
    RUN_TEST(test_second_entry_due_when_first_handled);
    return UNITY_END();
}
