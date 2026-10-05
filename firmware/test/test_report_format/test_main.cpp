#include <unity.h>
#include <string.h>
#include <ArduinoJson.h>
#include "../../src/report_format.h"
#include "../../src/publish_queue.h"

void setUp() {}
void tearDown() {}

static const char* ID = "0f8fad5b-d9cb-469f-a165-70867728950e";  // 36 chars

void test_status_on_with_id_and_duration() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatPumpStatus(buf, sizeof(buf), "on", "manual", nullptr, 120, ID, 1790751600));
    JsonDocument doc;
    TEST_ASSERT_FALSE(deserializeJson(doc, buf));
    TEST_ASSERT_EQUAL_STRING("on", doc["pump"]);
    TEST_ASSERT_EQUAL_STRING("manual", doc["trigger"]);
    TEST_ASSERT_EQUAL_UINT32(120, doc["duration_s"].as<uint32_t>());
    TEST_ASSERT_EQUAL_STRING(ID, doc["id"]);
    TEST_ASSERT_EQUAL_UINT32(1790751600, doc["ts"].as<uint32_t>());
    TEST_ASSERT_TRUE(doc["reason"].isNull());
}

void test_status_without_id_omits_the_key() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatPumpStatus(buf, sizeof(buf), "off", "schedule", "completed", 0, nullptr, 0));
    TEST_ASSERT_EQUAL_STRING(
        "{\"pump\":\"off\",\"trigger\":\"schedule\",\"reason\":\"completed\",\"ts\":0}", buf);
}

void test_status_with_empty_id_omits_the_key() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatPumpStatus(buf, sizeof(buf), "off", "manual", "command", 0, "", 5));
    TEST_ASSERT_NULL(strstr(buf, "\"id\""));
}

// Review Focus 3: a truncated report is invalid JSON, and the run would never close on the server
void test_longest_status_fits_a_queue_slot() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatPumpStatus(buf, sizeof(buf), "rejected", "schedule", "low_battery", 0, ID, 4294967295UL));
    JsonDocument doc;
    TEST_ASSERT_FALSE(deserializeJson(doc, buf));
    TEST_ASSERT_TRUE(formatPumpStatus(buf, sizeof(buf), "on", "schedule", nullptr, 600, ID, 4294967295UL));
}

void test_too_small_buffer_reports_truncation() {
    char buf[16];
    TEST_ASSERT_FALSE(formatPumpStatus(buf, sizeof(buf), "on", "manual", nullptr, 120, ID, 1));
    TEST_ASSERT_EQUAL_CHAR('\0', buf[sizeof(buf) - 1]);
}

void test_flow_with_id() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatFlow(buf, sizeof(buf), 0.35f, "manual", ID));
    JsonDocument doc;
    TEST_ASSERT_FALSE(deserializeJson(doc, buf));
    TEST_ASSERT_FLOAT_WITHIN(0.0005f, 0.35f, doc["liters"].as<float>());
    TEST_ASSERT_EQUAL_STRING(ID, doc["id"]);
}

void test_flow_without_id() {
    char buf[PUBLISH_QUEUE_PAYLOAD_LEN];
    TEST_ASSERT_TRUE(formatFlow(buf, sizeof(buf), 1.0f, "schedule", nullptr));
    TEST_ASSERT_EQUAL_STRING("{\"liters\":1.000,\"trigger\":\"schedule\"}", buf);
}

void test_moisture_with_clock_adds_ts() {
    char buf[256];
    const int percents[] = {42, 67, 55};
    TEST_ASSERT_TRUE(formatMoisture(buf, sizeof(buf), percents, 3, 1790751600));
    TEST_ASSERT_EQUAL_STRING("{\"sensor_0\":42,\"sensor_1\":67,\"sensor_2\":55,\"ts\":1790751600}", buf);
}

void test_moisture_without_clock_omits_ts() {
    char buf[256];
    const int percents[] = {42};
    TEST_ASSERT_TRUE(formatMoisture(buf, sizeof(buf), percents, 1, 0));
    TEST_ASSERT_EQUAL_STRING("{\"sensor_0\":42}", buf);
}

void test_battery_with_and_without_clock() {
    char buf[64];
    TEST_ASSERT_TRUE(formatBattery(buf, sizeof(buf), 78.1f, 3.91f, 1790751600));
    TEST_ASSERT_EQUAL_STRING("{\"soc\":78.1,\"voltage\":3.91,\"ts\":1790751600}", buf);
    TEST_ASSERT_TRUE(formatBattery(buf, sizeof(buf), 78.1f, 3.91f, 0));
    TEST_ASSERT_EQUAL_STRING("{\"soc\":78.1,\"voltage\":3.91}", buf);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_status_on_with_id_and_duration);
    RUN_TEST(test_status_without_id_omits_the_key);
    RUN_TEST(test_status_with_empty_id_omits_the_key);
    RUN_TEST(test_longest_status_fits_a_queue_slot);
    RUN_TEST(test_too_small_buffer_reports_truncation);
    RUN_TEST(test_flow_with_id);
    RUN_TEST(test_flow_without_id);
    RUN_TEST(test_moisture_with_clock_adds_ts);
    RUN_TEST(test_moisture_without_clock_omits_ts);
    RUN_TEST(test_battery_with_and_without_clock);
    return UNITY_END();
}
