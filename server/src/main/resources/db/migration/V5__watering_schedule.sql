-- One weekly watering schedule per instance, executed on the ESP32
CREATE TABLE watering_schedule (
    instance_id           UUID        PRIMARY KEY REFERENCES instance(id) ON DELETE CASCADE,
    version               INTEGER     NOT NULL,
    acknowledged_version  INTEGER,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE watering_schedule_entry (
    instance_id       UUID    NOT NULL REFERENCES watering_schedule(instance_id) ON DELETE CASCADE,
    position          INTEGER NOT NULL,
    time_of_day       TIME    NOT NULL,
    duration_seconds  INTEGER NOT NULL,
    days_mask         INTEGER NOT NULL,  -- bit0 = Monday ... bit6 = Sunday
    PRIMARY KEY (instance_id, position)
);
