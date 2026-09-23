#include "diagnostics.h"
#include "mqtt_client.h"
#include "battery_monitor.h"
#include "time_keeper.h"
#include <WiFi.h>
#include <esp_system.h>
#include "log.h"

static const char* resetReason = "unknown";

// Written from the WiFi event task, read from the loop task -- single-word
// values only, a torn read at worst mixes two consecutive drops.
static volatile bool     wifiLinkUp        = false;
static volatile uint16_t wifiDrops         = 0;
static volatile uint8_t  wifiLastDropReason = 0;
static volatile uint32_t wifiLastDropS     = 0;

static const char* resetReasonName(esp_reset_reason_t r) {
    switch (r) {
        case ESP_RST_POWERON:   return "poweron";
        case ESP_RST_EXT:       return "external";
        case ESP_RST_SW:        return "software";
        case ESP_RST_PANIC:     return "panic";
        case ESP_RST_INT_WDT:   return "int_wdt";
        case ESP_RST_TASK_WDT:  return "task_wdt";
        case ESP_RST_WDT:       return "wdt";
        case ESP_RST_DEEPSLEEP: return "deepsleep";
        case ESP_RST_BROWNOUT:  return "brownout";
        case ESP_RST_SDIO:      return "sdio";
        default:                return "unknown";
    }
}

void Diagnostics::begin() {
    resetReason = resetReasonName(esp_reset_reason());
    LOG_INFO("Diag: reset reason = %s", resetReason);

    WiFi.onEvent([](arduino_event_id_t, arduino_event_info_t) {
        wifiLinkUp = true;
    }, ARDUINO_EVENT_WIFI_STA_GOT_IP);

    WiFi.onEvent([](arduino_event_id_t, arduino_event_info_t info) {
        uint8_t reason = info.wifi_sta_disconnected.reason;
        // Only count up->down transitions; the reconnect loop in
        // wifi_manager fires further DISCONNECTED events while retrying.
        if (wifiLinkUp) {
            wifiLinkUp = false;
            wifiDrops++;
            wifiLastDropReason = reason;
            wifiLastDropS = millis() / 1000UL;
            // Serial, not LOG_*: this runs on the WiFi event task, and the
            // log ring buffer is only written from the loop task.
            Serial.printf("Diag: WiFi link lost, reason=%u\n", reason);
        }
    }, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
}

void Diagnostics::publishConnected() {
    _mqttConnects++;
    float voltage = batteryMonitor.sampleVoltage();

    char payload[224];
    snprintf(payload, sizeof(payload),
             "{\"event\":\"connected\",\"reset_reason\":\"%s\",\"uptime_s\":%lu,"
             "\"wifi_drops\":%u,\"wifi_drop_reason\":%u,\"wifi_last_drop_s\":%lu,"
             "\"mqtt_connects\":%u,\"rssi\":%d,\"voltage\":%.2f,\"clock_valid\":%s}",
             resetReason, millis() / 1000UL,
             wifiDrops, wifiLastDropReason, (unsigned long)wifiLastDropS,
             _mqttConnects, WiFi.RSSI(), voltage, timeKeeper.isValid() ? "true" : "false");
    // Direct publish (just connected): longer than a queue slot, and it
    // should go out before any stop reports queued during the outage.
    mqttClient.publish("plant/diag", payload);
}

Diagnostics diagnostics;
