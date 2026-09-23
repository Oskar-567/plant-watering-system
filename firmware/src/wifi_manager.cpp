#include "wifi_manager.h"
#include "../include/config.h"
#include <WiFi.h>

static unsigned long lastReconnectMs = 0;
static bool          wasConnected    = false;

void WiFiManager::begin() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.print("WiFi: connecting");
    unsigned long start = millis();
    while (!WiFi.isConnected() && millis() - start < 10000) {
        delay(500);
        Serial.print(".");
    }
    if (WiFi.isConnected()) {
        Serial.printf(" connected, IP=%s\n", WiFi.localIP().toString().c_str());
        WiFi.setSleep(WIFI_PS_MIN_MODEM);
        wasConnected = true;
    } else {
        Serial.println(" failed (will retry in loop)");
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
    Serial.println("WiFi: reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

bool WiFiManager::isConnected() const {
    return WiFi.isConnected();
}

WiFiManager wifiManager;
