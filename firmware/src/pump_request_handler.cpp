#include "pump_request_handler.h"
#include <Preferences.h>
#include "pump_controller.h"
#include "mqtt_client.h"
#include "time_keeper.h"
#include "report_format.h"
#include "publish_queue.h"
#include "log.h"
#include "../include/config.h"

static const char* NVS_NAMESPACE = "pumpreq";
static const uint32_t MAX_DURATION_S = MAX_PUMP_RUNTIME_MS / 1000UL;

void PumpRequestHandler::begin() {
    Preferences prefs;
    if (prefs.begin(NVS_NAMESPACE, true)) {  // namespace doesn't exist before the first save
        prefs.getString("last_id", _lastHandled, sizeof(_lastHandled));
        prefs.end();
    }
    LOG_INFO("Request: last handled id %s", _lastHandled[0] ? _lastHandled : "(none)");
}

void PumpRequestHandler::onRequestMessage(const char* payload) {
    if (!parsePumpRequest(payload, MAX_DURATION_S, _pending)) {
        LOG_WARN("Request: ignoring invalid payload: %s", payload);
        mqttClient.publishQueued("plant/diag", "{\"event\":\"request_rejected\"}");
        return;
    }
    if (_pending.present) {
        LOG_INFO("Request: %s, %lus, expires %lu", _pending.id,
                 (unsigned long)_pending.durationS, (unsigned long)_pending.expires);
    }
    _lastEvalMs = 0;  // decide on the next update()
}

void PumpRequestHandler::update() {
    if (!_pending.present) return;
    unsigned long nowMs = millis();
    if (_lastEvalMs != 0 && nowMs - _lastEvalMs < 1000) return;
    _lastEvalMs = nowMs;

    uint32_t now = timeKeeper.now();
    RequestDecision d = decidePumpRequest(_pending, _lastHandled, now, pumpController.isRunning());
    switch (d.action) {
        case RequestAction::Wait:
            return;  // no clock yet -- the server expires it if NTP never comes back
        case RequestAction::None:
            break;   // already handled (retained redelivery)
        case RequestAction::Reject: {
            markHandled(_pending.id);
            LOG_WARN("Request: %s rejected (%s)", _pending.id, d.reason);
            char payload[PUBLISH_QUEUE_PAYLOAD_LEN];
            formatPumpStatus(payload, sizeof(payload), "rejected", "manual", d.reason, 0, _pending.id, now);
            mqttClient.publishQueued("plant/status", payload);
            break;
        }
        case RequestAction::Start:
            markHandled(_pending.id);  // before the pump switches on
            LOG_INFO("Request: %s starting, %lus", _pending.id, (unsigned long)_pending.durationS);
            pumpController.start(_pending.durationS, PumpTrigger::Manual, _pending.id);  // refusals reported there
            break;
    }
    _pending = PumpRequest{};
}

void PumpRequestHandler::markHandled(const char* id) {
    strncpy(_lastHandled, id, REQUEST_ID_LEN);
    _lastHandled[REQUEST_ID_LEN] = '\0';
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, false);
    prefs.putString("last_id", _lastHandled);
    prefs.end();
}

PumpRequestHandler pumpRequestHandler;
