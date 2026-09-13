CREATE TABLE moisture_reading (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id  UUID             NOT NULL REFERENCES instance(id) ON DELETE CASCADE,
    sensor_index SMALLINT         NOT NULL,
    percent      DOUBLE PRECISION NOT NULL,
    measured_at  TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_moisture_instance_time ON moisture_reading (instance_id, measured_at);

CREATE TABLE battery_reading (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id  UUID             NOT NULL REFERENCES instance(id) ON DELETE CASCADE,
    soc          DOUBLE PRECISION NOT NULL,
    voltage      DOUBLE PRECISION NOT NULL,
    measured_at  TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_battery_instance_time ON battery_reading (instance_id, measured_at);
