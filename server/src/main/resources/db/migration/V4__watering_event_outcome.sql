-- Why a run ended / was not started, as reported by the ESP32:
-- completed, command, max_runtime, flow_stall, low_battery, busy, missed
ALTER TABLE watering_event ADD COLUMN outcome VARCHAR(30);
