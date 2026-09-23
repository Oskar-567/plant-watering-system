#include <unity.h>
#include "../../src/pump_command.h"

void setUp() {}
void tearDown() {}

static const uint32_t MAX_S = 600;

void test_start_with_duration() {
    PumpCommand c = parsePumpCommand("{\"action\":\"start\",\"duration_s\":600}", MAX_S);
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Start, (int)c.action);
    TEST_ASSERT_EQUAL_UINT32(600, c.durationS);
}
void test_stop() {
    PumpCommand c = parsePumpCommand("{\"action\":\"stop\"}", MAX_S);
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Stop, (int)c.action);
}
void test_start_without_duration_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid, (int)parsePumpCommand("{\"action\":\"start\"}", MAX_S).action);
}
void test_start_zero_duration_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid,
        (int)parsePumpCommand("{\"action\":\"start\",\"duration_s\":0}", MAX_S).action);
}
void test_start_above_max_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid,
        (int)parsePumpCommand("{\"action\":\"start\",\"duration_s\":601}", MAX_S).action);
}
void test_start_negative_duration_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid,
        (int)parsePumpCommand("{\"action\":\"start\",\"duration_s\":-5}", MAX_S).action);
}
void test_start_fractional_duration_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid,
        (int)parsePumpCommand("{\"action\":\"start\",\"duration_s\":60.5}", MAX_S).action);
}
void test_start_string_duration_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid,
        (int)parsePumpCommand("{\"action\":\"start\",\"duration_s\":\"600\"}", MAX_S).action);
}
void test_unknown_action_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid, (int)parsePumpCommand("{\"action\":\"flood\"}", MAX_S).action);
}
void test_malformed_json_is_invalid() {
    TEST_ASSERT_EQUAL_INT((int)PumpAction::Invalid, (int)parsePumpCommand("{\"action\":", MAX_S).action);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_start_with_duration);
    RUN_TEST(test_stop);
    RUN_TEST(test_start_without_duration_is_invalid);
    RUN_TEST(test_start_zero_duration_is_invalid);
    RUN_TEST(test_start_above_max_is_invalid);
    RUN_TEST(test_start_negative_duration_is_invalid);
    RUN_TEST(test_start_fractional_duration_is_invalid);
    RUN_TEST(test_start_string_duration_is_invalid);
    RUN_TEST(test_unknown_action_is_invalid);
    RUN_TEST(test_malformed_json_is_invalid);
    return UNITY_END();
}
