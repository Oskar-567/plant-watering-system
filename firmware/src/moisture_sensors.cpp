#include "moisture_sensors.h"
#include <algorithm>

static const uint8_t PINS[SENSOR_COUNT] = MOISTURE_PINS_INIT;
static const int     DRY[SENSOR_COUNT]  = MOISTURE_DRY_INIT;
static const int     WET[SENSOR_COUNT]  = MOISTURE_WET_INIT;

void MoistureSensors::begin() {
    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
        pinMode(PINS[i], INPUT);
    }
    memset(_values, 0, sizeof(_values));
}

void MoistureSensors::read() {
    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
        int raw = readMedian(PINS[i]);
        _values[i] = rawToPercent(raw, DRY[i], WET[i]);
        Serial.printf("Moisture sensor %u: raw=%d -> %d%%\n", i, raw, _values[i]);
    }
}

int MoistureSensors::getPercent(uint8_t index) const {
    if (index >= SENSOR_COUNT) return -1;
    return _values[index];
}

int MoistureSensors::readMedian(uint8_t pin) {
    int s[5];
    for (int i = 0; i < 5; i++) {
        s[i] = analogRead(pin);
        delay(10);
    }
    std::sort(s, s + 5);
    return s[2];
}

MoistureSensors moistureSensors;
