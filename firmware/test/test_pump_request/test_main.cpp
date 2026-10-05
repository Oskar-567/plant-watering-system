#include <unity.h>
#include <string.h>
#include "../../src/pump_request.h"

void setUp() {}
void tearDown() {}

static const uint32_t MAX_S = 600;
static const uint32_t NOW = 1790751000;
static const char* ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

static PumpRequest request(uint32_t expires) {
    PumpRequest r{};
    r.present = true;
    strcpy(r.id, ID);
    r.durationS = 120;
    r.expires = expires;
    return r;
}

// --- parse ---

void test_parses_a_request() {
    PumpRequest r{};
    TEST_ASSERT_TRUE(parsePumpRequest(
        "{\"id\":\"0f8fad5b-d9cb-469f-a165-70867728950e\",\"duration_s\":120,\"expires\":1790751720}", MAX_S, r));
    TEST_ASSERT_TRUE(r.present);
    TEST_ASSERT_EQUAL_STRING(ID, r.id);
    TEST_ASSERT_EQUAL_UINT32(120, r.durationS);
    TEST_ASSERT_EQUAL_UINT32(1790751720, r.expires);
}

void test_null_id_clears() {
    PumpRequest r = request(NOW + 60);
    TEST_ASSERT_TRUE(parsePumpRequest("{\"id\":null}", MAX_S, r));
    TEST_ASSERT_FALSE(r.present);
}

void test_malformed_payloads_are_rejected_and_leave_the_request_untouched() {
    const char* bad[] = {
        "{\"id\":",                                                      // broken JSON
        "{\"id\":42,\"duration_s\":120,\"expires\":1790751720}",         // id not a string
        "{\"id\":\"\",\"duration_s\":120,\"expires\":1790751720}",       // empty id
        "{\"id\":\"0f8fad5b-d9cb-469f-a165-70867728950e-x\",\"duration_s\":120,\"expires\":1}",  // id too long
        "{\"id\":\"a\",\"duration_s\":0,\"expires\":1790751720}",        // duration 0
        "{\"id\":\"a\",\"duration_s\":601,\"expires\":1790751720}",      // above max
        "{\"id\":\"a\",\"duration_s\":120}",                             // no expires
        "{\"id\":\"a\",\"duration_s\":120,\"expires\":0}",               // expires 0
    };
    for (const char* json : bad) {
        PumpRequest r = request(NOW + 60);
        TEST_ASSERT_FALSE_MESSAGE(parsePumpRequest(json, MAX_S, r), json);
        TEST_ASSERT_TRUE(r.present);
        TEST_ASSERT_EQUAL_STRING(ID, r.id);
    }
}

// --- decide ---

void test_no_request_does_nothing() {
    PumpRequest r{};
    TEST_ASSERT_EQUAL_INT((int)RequestAction::None, (int)decidePumpRequest(r, "", NOW, false).action);
}

// Review Focus 1: the retained request comes back on every reconnect
void test_same_id_as_last_handled_is_ignored() {
    TEST_ASSERT_EQUAL_INT((int)RequestAction::None,
                          (int)decidePumpRequest(request(NOW + 60), ID, NOW, false).action);
}

// Review Focus 4: keep it until NTP has synced
void test_request_without_clock_waits() {
    TEST_ASSERT_EQUAL_INT((int)RequestAction::Wait,
                          (int)decidePumpRequest(request(NOW + 60), "", 0, false).action);
}

void test_expired_request_is_rejected() {
    RequestDecision d = decidePumpRequest(request(NOW), "", NOW, false);
    TEST_ASSERT_EQUAL_INT((int)RequestAction::Reject, (int)d.action);
    TEST_ASSERT_EQUAL_STRING("expired", d.reason);
}

void test_request_while_pump_runs_is_rejected_busy() {
    RequestDecision d = decidePumpRequest(request(NOW + 60), "", NOW, true);
    TEST_ASSERT_EQUAL_INT((int)RequestAction::Reject, (int)d.action);
    TEST_ASSERT_EQUAL_STRING("busy", d.reason);
}

void test_valid_new_request_starts() {
    TEST_ASSERT_EQUAL_INT((int)RequestAction::Start,
                          (int)decidePumpRequest(request(NOW + 60), "older-id", NOW, false).action);
}

void test_same_id_wins_over_missing_clock() {
    // Already handled before a brownout: never "wait" and run it again later
    TEST_ASSERT_EQUAL_INT((int)RequestAction::None,
                          (int)decidePumpRequest(request(NOW + 60), ID, 0, false).action);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_parses_a_request);
    RUN_TEST(test_null_id_clears);
    RUN_TEST(test_malformed_payloads_are_rejected_and_leave_the_request_untouched);
    RUN_TEST(test_no_request_does_nothing);
    RUN_TEST(test_same_id_as_last_handled_is_ignored);
    RUN_TEST(test_request_without_clock_waits);
    RUN_TEST(test_expired_request_is_rejected);
    RUN_TEST(test_request_while_pump_runs_is_rejected_busy);
    RUN_TEST(test_valid_new_request_starts);
    RUN_TEST(test_same_id_wins_over_missing_clock);
    return UNITY_END();
}
