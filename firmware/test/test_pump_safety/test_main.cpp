#include <unity.h>
#include "../../src/pump_safety.h"

void setUp() {}
void tearDown() {}

// --- plausibility of fuel gauge readings ---
void test_voltage_zero_is_implausible()    { TEST_ASSERT_FALSE(isPlausibleCellVoltage(0.0f)); }
void test_voltage_nominal_is_plausible()   { TEST_ASSERT_TRUE(isPlausibleCellVoltage(3.7f)); }
void test_voltage_above_max_is_implausible() { TEST_ASSERT_FALSE(isPlausibleCellVoltage(5.1f)); }

// --- start gate ---
void test_start_allowed_when_battery_ok() {
    TEST_ASSERT_TRUE(batteryAllowsPumpStart(3.90f, 60.0f, 3.60f, 20.0f));
}
void test_start_refused_when_voltage_low() {
    TEST_ASSERT_FALSE(batteryAllowsPumpStart(3.55f, 60.0f, 3.60f, 20.0f));
}
void test_start_refused_when_soc_low() {
    TEST_ASSERT_FALSE(batteryAllowsPumpStart(3.90f, 12.0f, 3.60f, 20.0f));
}
void test_start_allowed_at_exact_thresholds() {
    TEST_ASSERT_TRUE(batteryAllowsPumpStart(3.60f, 20.0f, 3.60f, 20.0f));
}
void test_start_allowed_when_gauge_reading_invalid() {
    // I2C failure / missing gauge reads 0 V -- must not brick watering
    TEST_ASSERT_TRUE(batteryAllowsPumpStart(0.0f, 0.0f, 3.60f, 20.0f));
}

// --- debounced under-voltage detection while pumping ---
void test_detector_trips_after_required_consecutive_samples() {
    LowVoltageDetector d(3.30f, 3);
    TEST_ASSERT_FALSE(d.addSample(3.20f));
    TEST_ASSERT_FALSE(d.addSample(3.20f));
    TEST_ASSERT_TRUE(d.addSample(3.20f));
}
void test_detector_ignores_single_dip() {
    LowVoltageDetector d(3.30f, 3);
    TEST_ASSERT_FALSE(d.addSample(3.10f));
    TEST_ASSERT_FALSE(d.addSample(3.50f));
    TEST_ASSERT_FALSE(d.addSample(3.10f));
    TEST_ASSERT_FALSE(d.addSample(3.10f));
}
void test_detector_ignores_invalid_readings() {
    LowVoltageDetector d(3.30f, 2);
    TEST_ASSERT_FALSE(d.addSample(3.20f));
    TEST_ASSERT_FALSE(d.addSample(0.0f));   // failed read: neither counts nor resets
    TEST_ASSERT_TRUE(d.addSample(3.20f));
}
void test_detector_reset_clears_count() {
    LowVoltageDetector d(3.30f, 2);
    TEST_ASSERT_FALSE(d.addSample(3.20f));
    d.reset();
    TEST_ASSERT_FALSE(d.addSample(3.20f));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_voltage_zero_is_implausible);
    RUN_TEST(test_voltage_nominal_is_plausible);
    RUN_TEST(test_voltage_above_max_is_implausible);
    RUN_TEST(test_start_allowed_when_battery_ok);
    RUN_TEST(test_start_refused_when_voltage_low);
    RUN_TEST(test_start_refused_when_soc_low);
    RUN_TEST(test_start_allowed_at_exact_thresholds);
    RUN_TEST(test_start_allowed_when_gauge_reading_invalid);
    RUN_TEST(test_detector_trips_after_required_consecutive_samples);
    RUN_TEST(test_detector_ignores_single_dip);
    RUN_TEST(test_detector_ignores_invalid_readings);
    RUN_TEST(test_detector_reset_clears_count);
    return UNITY_END();
}
