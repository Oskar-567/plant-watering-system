#include "watering_scheduler.h"
#include "schedule_store.h"
#include "time_keeper.h"
#include "pump_controller.h"
#include "mqtt_client.h"
#include "log.h"
#include "../include/config.h"

static ScheduleStore store;
static const uint32_t MAX_DURATION_S = MAX_PUMP_RUNTIME_MS / 1000UL;

void WateringScheduler::begin() {
    char json[SCHEDULE_JSON_MAX_LEN];
    if (store.loadJson(json, sizeof(json)) && parseSchedule(json, MAX_DURATION_S, _schedule)) {
        store.loadState(_state);
        LOG_INFO("Schedule: loaded v%lu (%u entries) from NVS",
                 (unsigned long)_schedule.version, _schedule.count);
    } else {
        LOG_INFO("Schedule: none stored");
    }
}

const char* WateringScheduler::timezone() const {
    return _schedule.version ? _schedule.tz : DEFAULT_TZ;
}

void WateringScheduler::onScheduleMessage(const char* payload) {
    Schedule incoming;
    if (!parseSchedule(payload, MAX_DURATION_S, incoming)) {
        LOG_WARN("Schedule: rejected invalid payload: %s", payload);
        mqttClient.publishQueued("plant/diag", "{\"event\":\"schedule_rejected\"}");
        return;
    }

    // "!=" rather than ">": a server DB restore may legitimately go back in version.
    if (incoming.version != _schedule.version) {
        _schedule = incoming;
        _state = ScheduleState{};  // acceptedAt is set on the next update() with a valid clock
        store.saveJson(payload);
        store.saveState(_state);
        timeKeeper.setTimezone(_schedule.tz);
        LOG_INFO("Schedule: accepted v%lu (%u entries)",
                 (unsigned long)_schedule.version, _schedule.count);
    }
    publishAck();  // also for an unchanged version -> server sees it after every reconnect
}

void WateringScheduler::update() {
    if (_schedule.version == 0) return;

    unsigned long nowMs = millis();
    if (nowMs - _lastEvalMs < 1000) return;
    _lastEvalMs = nowMs;

    uint8_t  weekday;
    uint32_t secondOfDay;
    if (!timeKeeper.localNow(weekday, secondOfDay)) return;  // no valid clock -> never water by schedule
    uint32_t now = timeKeeper.now();

    if (_state.acceptedAt == 0) {
        _state.acceptedAt = now;
        store.saveState(_state);
        LOG_INFO("Schedule: v%lu active from %lu", (unsigned long)_schedule.version, (unsigned long)now);
    }

    if (pumpController.isRunning()) return;  // the catch-up window lets a due entry run afterwards

    ScheduleDecision d = evaluateSchedule(_schedule, _state, now, weekday, secondOfDay,
                                          SCHEDULE_CATCHUP_WINDOW_S);
    if (d.action == ScheduleAction::None) return;

    // Persist before acting: a crash or refusal must never re-trigger this occurrence.
    _state.lastHandled[d.entryIndex] = d.occurrence;
    store.saveState(_state);

    const ScheduleEntry& entry = _schedule.entries[d.entryIndex];
    if (d.action == ScheduleAction::Run) {
        LOG_INFO("Schedule: entry %u due (%lus late), running %lus", d.entryIndex,
                 (unsigned long)(now - d.occurrence), (unsigned long)entry.durationS);
        pumpController.start(entry.durationS, PumpTrigger::Schedule);  // a refusal is reported by the pump controller
    } else {
        LOG_WARN("Schedule: entry %u missed (%lus late)", d.entryIndex,
                 (unsigned long)(now - d.occurrence));
        char payload[96];
        snprintf(payload, sizeof(payload),
                 "{\"pump\":\"rejected\",\"trigger\":\"schedule\",\"reason\":\"missed\",\"ts\":%lu}",
                 (unsigned long)d.occurrence);
        mqttClient.publishQueued("plant/status", payload);
    }
}

void WateringScheduler::publishAck() {
    char payload[32];
    snprintf(payload, sizeof(payload), "{\"version\":%lu}", (unsigned long)_schedule.version);
    mqttClient.publishQueued("plant/schedule/ack", payload);
}

WateringScheduler wateringScheduler;
