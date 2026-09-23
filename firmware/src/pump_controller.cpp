#include "pump_controller.h"
#include "flow_meter.h"
#include "mqtt_client.h"
#include "wifi_manager.h"
#include "battery_monitor.h"

void PumpController::begin() {
    pinMode(RELAY_PIN, OUTPUT);
    setRelay(false);
}

void PumpController::start() {
    if (_running) return;

    // Fresh reading -- the periodic one can be up to BATTERY_INTERVAL_MS old.
    batteryMonitor.read();
    float voltage = batteryMonitor.getVoltage();
    float soc     = batteryMonitor.getSOC();
    if (!batteryAllowsPumpStart(voltage, soc, PUMP_MIN_START_VOLTAGE, PUMP_MIN_START_SOC)) {
        Serial.printf("Pump: start refused, battery too low (%.2fV, %.1f%%)\n", voltage, soc);
        mqttClient.publishQueued("plant/status", "{\"pump\":\"off\",\"reason\":\"low_battery\"}");
        char diag[96];
        snprintf(diag, sizeof(diag),
                 "{\"event\":\"pump_start_refused\",\"voltage\":%.2f,\"soc\":%.1f}", voltage, soc);
        mqttClient.publishQueued("plant/diag", diag);
        return;
    }
    if (!isPlausibleCellVoltage(voltage)) {
        Serial.println("Pump: no valid fuel gauge reading -- running without battery protection");
    }

    _running = true;
    flowMeter.resetCount();
    _startMs = millis();
    _lastFlowCheckMs = _startMs;
    _litersAtLastCheck = 0.0f;
    _lastVoltageCheckMs = _startMs;
    _minVoltage = 0.0f;
    trackMinVoltage(voltage);
    _lowVoltage.reset();
    setRelay(true);
    mqttClient.publishQueued("plant/status", "{\"pump\":\"on\"}");
    Serial.println("Pump: started");
}

void PumpController::stop() {
    stopWithReason(nullptr);
}

void PumpController::stopWithReason(const char* reason) {
    if (!_running) return;
    _running = false;
    setRelay(false);

    // Queued: after a link_lost stop these go out once MQTT is back, so the
    // server still learns the pump is off and how much was dispensed.
    char payload[32];
    snprintf(payload, sizeof(payload), "{\"liters\":%.3f}", flowMeter.getLiters());
    mqttClient.publishQueued("plant/sensors/flow", payload);

    if (reason) {
        char statusPayload[64];
        snprintf(statusPayload, sizeof(statusPayload), "{\"pump\":\"off\",\"reason\":\"%s\"}", reason);
        mqttClient.publishQueued("plant/status", statusPayload);
        Serial.printf("Pump: emergency stop (%s), %s dispensed\n", reason, payload);
    } else {
        mqttClient.publishQueued("plant/status", "{\"pump\":\"off\"}");
        Serial.printf("Pump: stopped, %s dispensed\n", payload);
    }

    char diag[128];
    snprintf(diag, sizeof(diag),
             "{\"event\":\"pump_stop\",\"reason\":\"%s\",\"runtime_s\":%lu,\"min_voltage\":%.2f}",
             reason ? reason : "command", (millis() - _startMs) / 1000UL, _minVoltage);
    mqttClient.publishQueued("plant/diag", diag);
}

void PumpController::update() {
    if (!_running) return;
    unsigned long now = millis();

    // No link = a stop command can't reach us. Stop now instead of running
    // blind until max runtime (e.g. WiFi collapsing from supply sag).
    if (!wifiManager.isConnected() || !mqttClient.isConnected()) {
        Serial.println("Pump: WiFi/MQTT link lost while pumping, stopping");
        stopWithReason("link_lost");
        return;
    }

    if (now - _startMs >= MAX_PUMP_RUNTIME_MS) {
        Serial.println("Pump: max runtime exceeded, stopping (MQTT stop command may be lost)");
        stopWithReason("max_runtime");
        return;
    }

    if (now - _lastVoltageCheckMs >= PUMP_VOLTAGE_CHECK_INTERVAL_MS) {
        _lastVoltageCheckMs = now;
        float voltage = batteryMonitor.sampleVoltage();
        trackMinVoltage(voltage);
        if (_lowVoltage.addSample(voltage)) {
            Serial.printf("Pump: battery sagging under load (%.2fV < %.2fV), stopping\n",
                          voltage, PUMP_MIN_RUN_VOLTAGE);
            stopWithReason("low_battery");
            return;
        }
    }

    if (now - _lastFlowCheckMs >= FLOW_CHECK_INTERVAL_MS) {
        float currentLiters = flowMeter.getLiters();
        float delta = currentLiters - _litersAtLastCheck;
        _litersAtLastCheck = currentLiters;
        _lastFlowCheckMs = now;

        if (delta < FLOW_STALL_THRESHOLD_L) {
            Serial.printf("Pump: flow stall (%.3f L in last %lus) -- empty tank or blockage?\n",
                          delta, FLOW_CHECK_INTERVAL_MS / 1000UL);
            stopWithReason("flow_stall");
        }
    }
}

bool PumpController::isRunning() const {
    return _running;
}

void PumpController::setRelay(bool on) {
    digitalWrite(RELAY_PIN, (RELAY_ACTIVE_LOW ? !on : on) ? HIGH : LOW);
}

void PumpController::trackMinVoltage(float voltage) {
    if (!isPlausibleCellVoltage(voltage)) return;
    if (_minVoltage == 0.0f || voltage < _minVoltage) _minVoltage = voltage;
}

PumpController pumpController;
