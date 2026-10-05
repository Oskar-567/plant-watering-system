#include "run_marker_store.h"
#include <Preferences.h>

static const char* NVS_NAMESPACE = "runmarker";

void RunMarkerStore::save(const RunMarker& marker) {
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, false);
    prefs.putBytes("m", &marker, sizeof(RunMarker));
    prefs.end();
}

bool RunMarkerStore::load(RunMarker& marker) {
    Preferences prefs;
    if (!prefs.begin(NVS_NAMESPACE, true)) return false;
    bool found = prefs.getBytesLength("m") == sizeof(RunMarker)
                 && prefs.getBytes("m", &marker, sizeof(RunMarker)) == sizeof(RunMarker);
    prefs.end();
    marker.id[REQUEST_ID_LEN] = '\0';
    return found;
}

void RunMarkerStore::clear() {
    Preferences prefs;
    if (!prefs.begin(NVS_NAMESPACE, false)) return;
    prefs.remove("m");
    prefs.end();
}

RunMarkerStore runMarkerStore;
