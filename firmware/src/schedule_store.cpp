#include "schedule_store.h"
#include <Preferences.h>

static const char* NVS_NAMESPACE = "schedule";

bool ScheduleStore::loadJson(char* buf, size_t len) {
    Preferences prefs;
    if (!prefs.begin(NVS_NAMESPACE, true)) return false;  // namespace doesn't exist before the first save
    size_t stored = prefs.getString("json", buf, len);
    prefs.end();
    return stored > 0;
}

void ScheduleStore::saveJson(const char* json) {
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, false);
    prefs.putString("json", json);
    prefs.end();
}

void ScheduleStore::loadState(ScheduleState& state) {
    state = ScheduleState{};
    Preferences prefs;
    if (!prefs.begin(NVS_NAMESPACE, true)) return;
    if (prefs.getBytesLength("state") == sizeof(ScheduleState)) {
        prefs.getBytes("state", &state, sizeof(ScheduleState));
    }
    prefs.end();
}

void ScheduleStore::saveState(const ScheduleState& state) {
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, false);
    prefs.putBytes("state", &state, sizeof(ScheduleState));
    prefs.end();
}
