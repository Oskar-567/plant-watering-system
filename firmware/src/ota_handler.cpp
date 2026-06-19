#include "ota_handler.h"
#include "../include/config.h"
#include <ArduinoOTA.h>

void OtaHandler::begin() {
    ArduinoOTA.setHostname(OTA_HOSTNAME);
    ArduinoOTA.setPassword(OTA_PASSWORD);
    ArduinoOTA.onStart([]() {
        Serial.println("OTA: update starting");
    });
    ArduinoOTA.onEnd([]() {
        Serial.println("OTA: done, rebooting");
    });
    ArduinoOTA.onError([](ota_error_t err) {
        Serial.printf("OTA: error [%u]\n", err);
    });
    ArduinoOTA.begin();
    Serial.printf("OTA: ready at hostname '%s'\n", OTA_HOSTNAME);
}

void OtaHandler::handle() {
    ArduinoOTA.handle();
}

OtaHandler otaHandler;
