#pragma once
#include <Arduino.h>

class PumpController {
public:
    void begin();
    void start();
    void stop();
    bool isRunning() const;

private:
    bool _running = false;
    void setRelay(bool on);
};

extern PumpController pumpController;
