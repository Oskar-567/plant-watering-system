#include <unity.h>
#include <stdio.h>
#include <string.h>
#include "../../src/publish_queue.h"

void setUp() {}
void tearDown() {}

// One pump run while offline queues on + flow + off + diag pump_stop.
static void queueRun(PublishQueue& q, const char* tag) {
    q.push("plant/status", tag);
    q.push("plant/sensors/flow", tag);
    q.push("plant/status", tag);
    q.push("plant/diag", tag);
}

void test_empty_initially() {
    PublishQueue q;
    TEST_ASSERT_TRUE(q.empty());
    TEST_ASSERT_EQUAL_UINT8(0, q.size());
}

void test_fifo_order() {
    PublishQueue q;
    q.push("a", "1");
    q.push("b", "2");
    TEST_ASSERT_EQUAL_STRING("a", q.front().topic);
    TEST_ASSERT_EQUAL_STRING("1", q.front().payload);
    q.pop();
    TEST_ASSERT_EQUAL_STRING("b", q.front().topic);
    q.pop();
    TEST_ASSERT_TRUE(q.empty());
}

// Schedule runs keep going through a WiFi outage: a day offline with two
// runs and a missed entry must not lose any report.
void test_holds_two_offline_runs_and_a_missed_entry() {
    PublishQueue q;
    queueRun(q, "run1");
    queueRun(q, "run2");
    q.push("plant/status", "missed");

    TEST_ASSERT_EQUAL_UINT8(9, q.size());
    TEST_ASSERT_EQUAL_STRING("run1", q.front().payload);
}

void test_full_queue_drops_oldest() {
    PublishQueue q;
    char payload[8];
    for (uint8_t i = 0; i <= PUBLISH_QUEUE_SIZE; i++) {
        snprintf(payload, sizeof(payload), "%u", i);
        TEST_ASSERT_EQUAL(i == PUBLISH_QUEUE_SIZE, q.full());
        q.push("t", payload);
    }
    TEST_ASSERT_EQUAL_UINT8(PUBLISH_QUEUE_SIZE, q.size());
    TEST_ASSERT_EQUAL_STRING("1", q.front().payload);  // "0" was dropped
}

void test_long_payload_is_truncated_and_terminated() {
    PublishQueue q;
    char longPayload[PUBLISH_QUEUE_PAYLOAD_LEN + 20];
    memset(longPayload, 'x', sizeof(longPayload) - 1);
    longPayload[sizeof(longPayload) - 1] = '\0';
    q.push("t", longPayload);
    TEST_ASSERT_EQUAL_size_t(PUBLISH_QUEUE_PAYLOAD_LEN - 1, strlen(q.front().payload));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_empty_initially);
    RUN_TEST(test_fifo_order);
    RUN_TEST(test_holds_two_offline_runs_and_a_missed_entry);
    RUN_TEST(test_full_queue_drops_oldest);
    RUN_TEST(test_long_payload_is_truncated_and_terminated);
    return UNITY_END();
}
