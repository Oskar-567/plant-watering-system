#include <unity.h>
#include "../../src/wake_policy.h"

void setUp() {}
void tearDown() {}

static const uint32_t NOW = 1790751000;

void test_parses_until() {
    uint32_t until = 0;
    TEST_ASSERT_TRUE(parseAwakeUntil("{\"until\":1790752000}", until));
    TEST_ASSERT_EQUAL_UINT32(1790752000, until);
}

void test_until_zero_is_valid_and_closes_the_window() {
    uint32_t until = 123;
    TEST_ASSERT_TRUE(parseAwakeUntil("{\"until\":0}", until));
    TEST_ASSERT_EQUAL_UINT32(0, until);
}

void test_malformed_awake_payload_is_rejected() {
    uint32_t until = 123;
    TEST_ASSERT_FALSE(parseAwakeUntil("{\"until\":\"soon\"}", until));
    TEST_ASSERT_FALSE(parseAwakeUntil("{}", until));
    TEST_ASSERT_FALSE(parseAwakeUntil("{\"until\":", until));
    TEST_ASSERT_EQUAL_UINT32(123, until);
}

void test_window_in_the_future_is_kept() {
    TEST_ASSERT_EQUAL_UINT32(NOW + 600, effectiveAwakeUntil(NOW + 600, NOW, AWAKE_MAX_S));
}

void test_forgotten_long_window_is_capped() {
    TEST_ASSERT_EQUAL_UINT32(NOW + AWAKE_MAX_S, effectiveAwakeUntil(NOW + 86400, NOW, AWAKE_MAX_S));
}

void test_past_window_or_no_clock_means_no_window() {
    TEST_ASSERT_EQUAL_UINT32(0, effectiveAwakeUntil(NOW - 1, NOW, AWAKE_MAX_S));
    TEST_ASSERT_EQUAL_UINT32(0, effectiveAwakeUntil(NOW, NOW, AWAKE_MAX_S));
    TEST_ASSERT_EQUAL_UINT32(0, effectiveAwakeUntil(NOW + 600, 0, AWAKE_MAX_S));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_parses_until);
    RUN_TEST(test_until_zero_is_valid_and_closes_the_window);
    RUN_TEST(test_malformed_awake_payload_is_rejected);
    RUN_TEST(test_window_in_the_future_is_kept);
    RUN_TEST(test_forgotten_long_window_is_capped);
    RUN_TEST(test_past_window_or_no_clock_means_no_window);
    return UNITY_END();
}
