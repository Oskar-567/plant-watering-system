-- Deep sleep v1: a manual start is a request the ESP32 picks up on its next wake.
-- started_at is set only when the device reports "on" -- null = never ran.
ALTER TABLE watering_event ALTER COLUMN started_at DROP NOT NULL;
ALTER TABLE watering_event ADD COLUMN requested_at         TIMESTAMPTZ;
ALTER TABLE watering_event ADD COLUMN requested_duration_s INTEGER;
ALTER TABLE watering_event ADD COLUMN expires_at           TIMESTAMPTZ;

-- Receive time of the latest MQTT message from the device (only moves forward)
ALTER TABLE instance ADD COLUMN last_seen_at TIMESTAMPTZ;
