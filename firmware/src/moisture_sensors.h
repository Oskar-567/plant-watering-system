#pragma once
#include <Arduino.h>
#include "moisture_math.h"
#include "../include/config.h"

class MoistureSensors {
public:
    void begin();
    void read();
    int getPercent(uint8_t index) const;

private:
    int _values[SENSOR_COUNT];
    int readMedian(uint8_t pin);
};

extern MoistureSensors moistureSensors;
