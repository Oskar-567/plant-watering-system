#include <unity.h>
#include "../../src/flow_math.h"
#include "../../src/moisture_math.h"

void setUp() {}
void tearDown() {}

void test_pulses_zero()         { TEST_ASSERT_FLOAT_WITHIN(0.001f, 0.0f, pulsesToLiters(0,   450)); }
void test_pulses_one_liter()    { TEST_ASSERT_FLOAT_WITHIN(0.001f, 1.0f, pulsesToLiters(450, 450)); }
void test_pulses_half_liter()   { TEST_ASSERT_FLOAT_WITHIN(0.001f, 0.5f, pulsesToLiters(225, 450)); }
void test_pulses_zero_divisor() { TEST_ASSERT_FLOAT_WITHIN(0.001f, 0.0f, pulsesToLiters(100, 0));   }

void test_moisture_dry()        { TEST_ASSERT_EQUAL_INT(0,   rawToPercent(3200, 3200, 1400)); }
void test_moisture_wet()        { TEST_ASSERT_EQUAL_INT(100, rawToPercent(1400, 3200, 1400)); }
void test_moisture_mid()        { TEST_ASSERT_EQUAL_INT(50,  rawToPercent(2300, 3200, 1400)); }
void test_moisture_above_dry()  { TEST_ASSERT_EQUAL_INT(0,   rawToPercent(3500, 3200, 1400)); }
void test_moisture_below_wet()  { TEST_ASSERT_EQUAL_INT(100, rawToPercent(1000, 3200, 1400)); }
void test_moisture_equal_vals() { TEST_ASSERT_EQUAL_INT(0,   rawToPercent(2000, 2000, 2000)); }

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_pulses_zero);
    RUN_TEST(test_pulses_one_liter);
    RUN_TEST(test_pulses_half_liter);
    RUN_TEST(test_pulses_zero_divisor);
    RUN_TEST(test_moisture_dry);
    RUN_TEST(test_moisture_wet);
    RUN_TEST(test_moisture_mid);
    RUN_TEST(test_moisture_above_dry);
    RUN_TEST(test_moisture_below_wet);
    RUN_TEST(test_moisture_equal_vals);
    return UNITY_END();
}
