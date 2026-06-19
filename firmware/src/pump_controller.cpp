#include "pump_controller.h"
#include "flow_meter.h"
#include "mqtt_client.h"
#include "../include/config.h"

void PumpController::begin() {
    pinMode(RELAY_PIN, OUTPUT);
    setRelay(false);
}

void PumpController::start() {
    if (_running) return;
    _running = true;
    flowMeter.resetCount();
    setRelay(true);
    mqttClient.publish("plant/status", "{\"pump\":\"on\"}");
    Serial.println("Pump: started");
}

void PumpController::stop() {
    if (!_running) return;
    _running = false;
    setRelay(false);

    char payload[32];
    snprintf(payload, sizeof(payload), "{\"liters\":%.3f}", flowMeter.getLiters());
    mqttClient.publish("plant/sensors/flow", payload);
    mqttClient.publish("plant/status", "{\"pump\":\"off\"}");
    Serial.printf("Pump: stopped, %s dispensed\n", payload);
}

bool PumpController::isRunning() const {
    return _running;
}

void PumpController::setRelay(bool on) {
    digitalWrite(RELAY_PIN, (RELAY_ACTIVE_LOW ? !on : on) ? HIGH : LOW);
}

PumpController pumpController;
