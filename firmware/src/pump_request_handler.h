#pragma once
#include <Arduino.h>
#include "pump_request.h"

// Glue between the retained plant/pump/request, NVS and the pump. The
// decision lives in pump_request.h. The last handled id is persisted before
// the pump starts, so neither a reconnect (retained redelivery) nor a crash
// can run a request twice.
class PumpRequestHandler {
public:
    void begin();                              // load the last handled id from NVS
    void onRequestMessage(const char* payload);
    void update();                             // call every loop()

private:
    PumpRequest   _pending{};
    char          _lastHandled[REQUEST_ID_LEN + 1] = "";
    unsigned long _lastEvalMs = 0;

    void markHandled(const char* id);
};

extern PumpRequestHandler pumpRequestHandler;
