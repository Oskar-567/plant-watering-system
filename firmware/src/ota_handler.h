#pragma once
#include <Arduino.h>

class OtaHandler {
public:
    void begin();
    void handle();
};

extern OtaHandler otaHandler;
