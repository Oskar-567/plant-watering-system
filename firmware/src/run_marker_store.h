#pragma once
#include <Arduino.h>
#include "run_marker.h"

// NVS persistence (namespace "runmarker"): two writes per pump run.
class RunMarkerStore {
public:
    void save(const RunMarker& marker);
    bool load(RunMarker& marker);  // false if none stored
    void clear();
};

extern RunMarkerStore runMarkerStore;
