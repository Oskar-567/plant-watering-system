#include "ota_handler.h"
#include "../include/config.h"
#include <ArduinoOTA.h>
#include <esp_task_wdt.h>
#include "log.h"

void OtaHandler::begin() {
    ArduinoOTA.setHostname(OTA_HOSTNAME);
    ArduinoOTA.setPassword(OTA_PASSWORD);
    ArduinoOTA.onStart([]() {
        LOG_INFO("OTA: update starting");
    });
    // An upload blocks loop() for longer than the watchdog timeout --
    // keep feeding it so the update isn't killed half-way.
    ArduinoOTA.onProgress([](unsigned int, unsigned int) {
        esp_task_wdt_reset();
    });
    ArduinoOTA.onEnd([]() {
        LOG_INFO("OTA: done, rebooting");
    });
    ArduinoOTA.onError([](ota_error_t err) {
        LOG_ERROR("OTA: error [%u]", err);
    });
    ArduinoOTA.begin();
    LOG_INFO("OTA: ready at hostname '%s'", OTA_HOSTNAME);
}

void OtaHandler::handle() {
    ArduinoOTA.handle();
}

OtaHandler otaHandler;
