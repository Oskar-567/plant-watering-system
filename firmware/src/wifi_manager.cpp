#include "wifi_manager.h"
#include "../include/config.h"
#include <WiFi.h>
#include "log.h"

static unsigned long lastReconnectMs = 0;
static bool          wasConnected    = false;

void WiFiManager::begin() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    LOG_INFO("WiFi: connecting to %s", WIFI_SSID);
    unsigned long start = millis();
    while (!WiFi.isConnected() && millis() - start < 10000) {
        delay(500);
    }
    if (WiFi.isConnected()) {
        LOG_INFO("WiFi: connected, IP=%s", WiFi.localIP().toString().c_str());
        WiFi.setSleep(WIFI_PS_MIN_MODEM);
        wasConnected = true;
    } else {
        LOG_WARN("WiFi: connect timed out, retrying in loop");
    }
}

void WiFiManager::update() {
    bool connected = WiFi.isConnected();
    if (connected && !wasConnected) {
        // Connection came up asynchronously after begin() timed out -- modem
        // sleep must be (re-)applied once the driver actually has a link.
        WiFi.setSleep(WIFI_PS_MIN_MODEM);
    }
    wasConnected = connected;
    if (connected) return;

    if (millis() - lastReconnectMs < 5000) return;
    lastReconnectMs = millis();
    LOG_INFO("WiFi: reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

bool WiFiManager::isConnected() const {
    return WiFi.isConnected();
}

WiFiManager wifiManager;
