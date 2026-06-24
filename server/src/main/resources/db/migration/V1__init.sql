CREATE TABLE instance (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    mqtt_prefix   VARCHAR(255) NOT NULL,
    has_pump      BOOLEAN      NOT NULL DEFAULT FALSE,
    has_battery   BOOLEAN      NOT NULL DEFAULT FALSE,
    sensor_count  INTEGER      NOT NULL DEFAULT 1,
    latitude      DECIMAL(9, 6),
    longitude     DECIMAL(9, 6),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE watering_event (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id   UUID         NOT NULL REFERENCES instance(id) ON DELETE CASCADE,
    started_at    TIMESTAMPTZ  NOT NULL,
    stopped_at    TIMESTAMPTZ,
    liters        DECIMAL(8, 3),
    triggered_by  VARCHAR(50)
);