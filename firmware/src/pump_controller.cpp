#include "pump_controller.h"
#include "flow_meter.h"
#include "mqtt_client.h"
#include "battery_monitor.h"
#include "log.h"

static const char* triggerName(PumpTrigger trigger) {
    return trigger == PumpTrigger::Schedule ? "schedule" : "manual";
}

void PumpController::begin() {
    pinMode(RELAY_PIN, OUTPUT);
    setRelay(false);
}

bool PumpController::start(uint32_t durationS, PumpTrigger trigger) {
    if (_running) {
        LOG_WARN("Pump: %s start ignored, already running", triggerName(trigger));
        publishStatus("rejected", trigger, "busy");
        return false;
    }

    // Fresh reading -- the periodic one can be up to BATTERY_INTERVAL_MS old.
    batteryMonitor.read();
    float voltage = batteryMonitor.getVoltage();
    float soc     = batteryMonitor.getSOC();
    if (!batteryAllowsPumpStart(voltage, soc, PUMP_MIN_START_VOLTAGE, PUMP_MIN_START_SOC)) {
        LOG_WARN("Pump: %s start refused, battery too low (%.2fV, %.1f%%)",
                 triggerName(trigger), voltage, soc);
        publishStatus("rejected", trigger, "low_battery");
        char diag[96];
        snprintf(diag, sizeof(diag),
                 "{\"event\":\"pump_start_refused\",\"voltage\":%.2f,\"soc\":%.1f}", voltage, soc);
        mqttClient.publishQueued("plant/diag", diag);
        return false;
    }
    if (!isPlausibleCellVoltage(voltage)) {
        LOG_WARN("Pump: no valid fuel gauge reading -- running without battery protection");
    }

    _running = true;
    _trigger = trigger;
    flowMeter.resetCount();
    _startMs = millis();
    _durationMs = durationS * 1000UL;
    _lastFlowCheckMs = _startMs;
    _litersAtLastCheck = 0.0f;
    _lastVoltageCheckMs = _startMs;
    _minVoltage = 0.0f;
    trackMinVoltage(voltage);
    _lowVoltage.reset();
    setRelay(true);
    publishStatus("on", trigger, nullptr);
    LOG_INFO("Pump: started (%s, %lus)", triggerName(trigger), (unsigned long)durationS);
    return true;
}

void PumpController::stop(const char* reason) {
    if (!_running) return;
    _running = false;
    setRelay(false);

    // Queued: if the link is down these go out after reconnect, so the server
    // still learns the run ended and how much was dispensed. Flow first --
    // the server closes the event on "off".
    char flow[64];
    snprintf(flow, sizeof(flow), "{\"liters\":%.3f,\"trigger\":\"%s\"}",
             flowMeter.getLiters(), triggerName(_trigger));
    mqttClient.publishQueued("plant/sensors/flow", flow);
    publishStatus("off", _trigger, reason);

    // Planned ends are info; safety cutoffs stay warnings.
    bool planned = strcmp(reason, "completed") == 0 || strcmp(reason, "command") == 0;
    if (planned) {
        LOG_INFO("Pump: stopped (%s, %s), %s", triggerName(_trigger), reason, flow);
    } else {
        LOG_WARN("Pump: emergency stop (%s, %s), %s", triggerName(_trigger), reason, flow);
    }

    char diag[128];
    snprintf(diag, sizeof(diag),
             "{\"event\":\"pump_stop\",\"reason\":\"%s\",\"runtime_s\":%lu,\"min_voltage\":%.2f}",
             reason, (millis() - _startMs) / 1000UL, _minVoltage);
    mqttClient.publishQueued("plant/diag", diag);
}

void PumpController::update() {
    if (!_running) return;
    unsigned long now = millis();

    // No link-loss cutoff any more: every run is bounded by its duration, and
    // schedule runs must keep going through a WiFi outage.

    // Before max runtime, so a run of exactly MAX_PUMP_RUNTIME_MS ends as "completed".
    if (now - _startMs >= _durationMs) {
        stop("completed");
        return;
    }

    // Last line of defence -- durations are already validated <= max.
    if (now - _startMs >= MAX_PUMP_RUNTIME_MS) {
        LOG_WARN("Pump: max runtime exceeded, stopping");
        stop("max_runtime");
        return;
    }

    if (now - _lastVoltageCheckMs >= PUMP_VOLTAGE_CHECK_INTERVAL_MS) {
        _lastVoltageCheckMs = now;
        float voltage = batteryMonitor.sampleVoltage();
        trackMinVoltage(voltage);
        if (_lowVoltage.addSample(voltage)) {
            LOG_WARN("Pump: battery sagging under load (%.2fV < %.2fV), stopping",
                     voltage, PUMP_MIN_RUN_VOLTAGE);
            stop("low_battery");
            return;
        }
    }

    if (now - _lastFlowCheckMs >= FLOW_CHECK_INTERVAL_MS) {
        float currentLiters = flowMeter.getLiters();
        float delta = currentLiters - _litersAtLastCheck;
        _litersAtLastCheck = currentLiters;
        _lastFlowCheckMs = now;

        if (delta < FLOW_STALL_THRESHOLD_L) {
            LOG_WARN("Pump: flow stall (%.3f L in last %lus) -- empty tank or blockage?",
                     delta, FLOW_CHECK_INTERVAL_MS / 1000UL);
            stop("flow_stall");
        }
    }
}

bool PumpController::isRunning() const {
    return _running;
}

void PumpController::setRelay(bool on) {
    digitalWrite(RELAY_PIN, (RELAY_ACTIVE_LOW ? !on : on) ? HIGH : LOW);
}

void PumpController::publishStatus(const char* pump, PumpTrigger trigger, const char* reason) {
    char payload[128];
    int n = snprintf(payload, sizeof(payload), "{\"pump\":\"%s\",\"trigger\":\"%s\"",
                     pump, triggerName(trigger));
    if (reason) {
        n += snprintf(payload + n, sizeof(payload) - n, ",\"reason\":\"%s\"", reason);
    }
    if (strcmp(pump, "on") == 0) {
        n += snprintf(payload + n, sizeof(payload) - n, ",\"duration_s\":%lu", _durationMs / 1000UL);
    }
    snprintf(payload + n, sizeof(payload) - n, "}");
    mqttClient.publishQueued("plant/status", payload);
}

void PumpController::trackMinVoltage(float voltage) {
    if (!isPlausibleCellVoltage(voltage)) return;
    if (_minVoltage == 0.0f || voltage < _minVoltage) _minVoltage = voltage;
}

PumpController pumpController;
