#include <unity.h>
#include "../../src/schedule_model.h"

void setUp() {}
void tearDown() {}

static const uint32_t MAX_S = 600;

static bool parse(const char* json, Schedule& out) { return parseSchedule(json, MAX_S, out); }

// --- parseTimeOfDay ---
void test_time_valid() {
    uint32_t s = 0;
    TEST_ASSERT_TRUE(parseTimeOfDay("06:30", s));
    TEST_ASSERT_EQUAL_UINT32(6 * 3600 + 30 * 60, s);
    TEST_ASSERT_TRUE(parseTimeOfDay("23:59", s));
    TEST_ASSERT_EQUAL_UINT32(23 * 3600 + 59 * 60, s);
    TEST_ASSERT_TRUE(parseTimeOfDay("00:00", s));
    TEST_ASSERT_EQUAL_UINT32(0, s);
}
void test_time_invalid() {
    uint32_t s = 0;
    TEST_ASSERT_FALSE(parseTimeOfDay("24:00", s));
    TEST_ASSERT_FALSE(parseTimeOfDay("12:60", s));
    TEST_ASSERT_FALSE(parseTimeOfDay("7:00", s));
    TEST_ASSERT_FALSE(parseTimeOfDay("12-00", s));
    TEST_ASSERT_FALSE(parseTimeOfDay("1a:00", s));
    TEST_ASSERT_FALSE(parseTimeOfDay(nullptr, s));
}

// --- parseSchedule ---
void test_schedule_valid() {
    Schedule s;
    TEST_ASSERT_TRUE(parse(
        "{\"version\":7,\"tz\":\"CET-1CEST,M3.5.0,M10.5.0/3\",\"entries\":["
        "{\"t\":\"06:30\",\"d\":300,\"w\":127},{\"t\":\"20:00\",\"d\":600,\"w\":21}]}", s));
    TEST_ASSERT_EQUAL_UINT32(7, s.version);
    TEST_ASSERT_EQUAL_STRING("CET-1CEST,M3.5.0,M10.5.0/3", s.tz);
    TEST_ASSERT_EQUAL_UINT8(2, s.count);
    TEST_ASSERT_EQUAL_UINT32(23400, s.entries[0].secondOfDay);
    TEST_ASSERT_EQUAL_UINT32(300, s.entries[0].durationS);
    TEST_ASSERT_EQUAL_UINT8(127, s.entries[0].daysMask);
    TEST_ASSERT_EQUAL_UINT32(72000, s.entries[1].secondOfDay);
    TEST_ASSERT_EQUAL_UINT32(600, s.entries[1].durationS);
    TEST_ASSERT_EQUAL_UINT8(21, s.entries[1].daysMask);
}
void test_schedule_empty_entries_is_valid() {
    Schedule s;
    TEST_ASSERT_TRUE(parse("{\"version\":2,\"tz\":\"UTC0\",\"entries\":[]}", s));
    TEST_ASSERT_EQUAL_UINT8(0, s.count);
}
void test_schedule_malformed_json() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":2,", s));
}
void test_schedule_version_missing_or_zero() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"tz\":\"UTC0\",\"entries\":[]}", s));
    TEST_ASSERT_FALSE(parse("{\"version\":0,\"tz\":\"UTC0\",\"entries\":[]}", s));
}
void test_schedule_tz_missing_or_too_long() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"entries\":[]}", s));
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"\",\"entries\":[]}", s));
    TEST_ASSERT_FALSE(parse(
        "{\"version\":1,\"tz\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUV\",\"entries\":[]}", s)); // 48 chars
}
void test_schedule_entries_missing() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\"}", s));
}
void test_schedule_too_many_entries() {
    Schedule s;
    TEST_ASSERT_FALSE(parse(
        "{\"version\":1,\"tz\":\"UTC0\",\"entries\":["
        "{\"t\":\"01:00\",\"d\":60,\"w\":1},{\"t\":\"02:00\",\"d\":60,\"w\":1},{\"t\":\"03:00\",\"d\":60,\"w\":1},"
        "{\"t\":\"04:00\",\"d\":60,\"w\":1},{\"t\":\"05:00\",\"d\":60,\"w\":1},{\"t\":\"06:00\",\"d\":60,\"w\":1},"
        "{\"t\":\"07:00\",\"d\":60,\"w\":1},{\"t\":\"08:00\",\"d\":60,\"w\":1},{\"t\":\"09:00\",\"d\":60,\"w\":1}]}", s));
}
void test_schedule_entry_bad_time() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"24:00\",\"d\":60,\"w\":1}]}", s));
}
void test_schedule_entry_bad_duration() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"12:00\",\"d\":0,\"w\":1}]}", s));
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"12:00\",\"d\":601,\"w\":1}]}", s));
}
void test_schedule_entry_bad_days() {
    Schedule s;
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"12:00\",\"d\":60,\"w\":0}]}", s));
    TEST_ASSERT_FALSE(parse("{\"version\":1,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"12:00\",\"d\":60,\"w\":128}]}", s));
}
void test_schedule_failure_leaves_output_untouched() {
    Schedule s;
    s.version = 3;
    s.count = 1;
    TEST_ASSERT_FALSE(parse("{\"version\":9,\"tz\":\"UTC0\",\"entries\":[{\"t\":\"99:00\",\"d\":60,\"w\":1}]}", s));
    TEST_ASSERT_EQUAL_UINT32(3, s.version);
    TEST_ASSERT_EQUAL_UINT8(1, s.count);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_time_valid);
    RUN_TEST(test_time_invalid);
    RUN_TEST(test_schedule_valid);
    RUN_TEST(test_schedule_empty_entries_is_valid);
    RUN_TEST(test_schedule_malformed_json);
    RUN_TEST(test_schedule_version_missing_or_zero);
    RUN_TEST(test_schedule_tz_missing_or_too_long);
    RUN_TEST(test_schedule_entries_missing);
    RUN_TEST(test_schedule_too_many_entries);
    RUN_TEST(test_schedule_entry_bad_time);
    RUN_TEST(test_schedule_entry_bad_duration);
    RUN_TEST(test_schedule_entry_bad_days);
    RUN_TEST(test_schedule_failure_leaves_output_untouched);
    return UNITY_END();
}
