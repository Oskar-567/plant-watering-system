#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>

// Pure FIFO for MQTT messages that must survive a disconnect (no Arduino
// deps, unit-tested in test/test_publish_queue). When full, the oldest
// message is dropped -- the caller logs it via front() before pushing.

// Sized for schedule runs during a WiFi outage: each run queues 4 messages
// (on, flow, off, diag), so 16 holds several runs plus missed reports.
// 16 x ~136 B ~ 2.2 KB RAM.
static const uint8_t PUBLISH_QUEUE_SIZE        = 16;
static const size_t  PUBLISH_QUEUE_PAYLOAD_LEN = 128;

struct QueuedMessage {
    const char* topic;  // string literal / static -- only the pointer is kept
    char        payload[PUBLISH_QUEUE_PAYLOAD_LEN];
};

class PublishQueue {
public:
    bool    empty() const { return _count == 0; }
    bool    full()  const { return _count == PUBLISH_QUEUE_SIZE; }
    uint8_t size()  const { return _count; }

    const QueuedMessage& front() const { return _items[_head]; }

    void pop() {
        if (_count == 0) return;
        _head = (_head + 1) % PUBLISH_QUEUE_SIZE;
        _count--;
    }

    void push(const char* topic, const char* payload) {
        if (full()) pop();
        QueuedMessage& slot = _items[(_head + _count) % PUBLISH_QUEUE_SIZE];
        slot.topic = topic;
        strncpy(slot.payload, payload, PUBLISH_QUEUE_PAYLOAD_LEN - 1);
        slot.payload[PUBLISH_QUEUE_PAYLOAD_LEN - 1] = '\0';
        _count++;
    }

private:
    QueuedMessage _items[PUBLISH_QUEUE_SIZE];
    uint8_t       _head  = 0;
    uint8_t       _count = 0;
};
