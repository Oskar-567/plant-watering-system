package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.BatteryPoint;
import com.plant_watering_system.server.dto.MoisturePoint;
import com.plant_watering_system.server.model.BatteryReading;
import com.plant_watering_system.server.model.MoistureReading;
import com.plant_watering_system.server.repository.BatteryReadingRepository;
import com.plant_watering_system.server.repository.MoistureReadingRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SensorReadingService {

    private static final Duration MAX_CLOCK_AHEAD = Duration.ofDays(1);

    private final MoistureReadingRepository moistureReadingRepository;
    private final BatteryReadingRepository batteryReadingRepository;

    public SensorReadingService(
            MoistureReadingRepository moistureReadingRepository,
            BatteryReadingRepository batteryReadingRepository) {
        this.moistureReadingRepository = moistureReadingRepository;
        this.batteryReadingRepository = batteryReadingRepository;
    }

    // ts = device epoch seconds, 0 = no valid clock on the device
    public void recordMoisture(UUID instanceId, int sensorIndex, double percent, long ts) {
        MoistureReading reading = new MoistureReading();
        reading.setInstanceId(instanceId);
        reading.setSensorIndex((short) sensorIndex);
        reading.setPercent(percent);
        reading.setMeasuredAt(measuredAt(ts));
        moistureReadingRepository.save(reading);
    }

    public void recordBattery(UUID instanceId, double soc, double voltage, long ts) {
        BatteryReading reading = new BatteryReading();
        reading.setInstanceId(instanceId);
        reading.setSoc(soc);
        reading.setVoltage(voltage);
        reading.setMeasuredAt(measuredAt(ts));
        batteryReadingRepository.save(reading);
    }

    // A clock far in the future is a broken RTC, not a reading from tomorrow -- fall back to receive time.
    static OffsetDateTime measuredAt(long ts) {
        OffsetDateTime now = OffsetDateTime.now();
        if (ts <= 0) return now;
        OffsetDateTime device = OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC);
        return device.isAfter(now.plus(MAX_CLOCK_AHEAD)) ? now : device;
    }

    public List<MoisturePoint> getMoisture(UUID instanceId, Duration range) {
        OffsetDateTime since = OffsetDateTime.now().minus(range);
        return moistureReadingRepository
                .findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(instanceId, since)
                .stream()
                .map(r -> new MoisturePoint(r.getMeasuredAt().toInstant(), r.getSensorIndex(), r.getPercent()))
                .toList();
    }

    public List<BatteryPoint> getBattery(UUID instanceId, Duration range) {
        OffsetDateTime since = OffsetDateTime.now().minus(range);
        return batteryReadingRepository
                .findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(instanceId, since)
                .stream()
                .map(r -> new BatteryPoint(r.getMeasuredAt().toInstant(), r.getSoc(), r.getVoltage()))
                .toList();
    }
}
