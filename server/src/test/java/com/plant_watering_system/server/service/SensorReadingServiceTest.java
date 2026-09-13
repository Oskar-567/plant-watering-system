package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.BatteryPoint;
import com.plant_watering_system.server.dto.MoisturePoint;
import com.plant_watering_system.server.model.BatteryReading;
import com.plant_watering_system.server.model.MoistureReading;
import com.plant_watering_system.server.repository.BatteryReadingRepository;
import com.plant_watering_system.server.repository.MoistureReadingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorReadingServiceTest {

    @Mock MoistureReadingRepository moistureReadingRepository;
    @Mock BatteryReadingRepository batteryReadingRepository;

    private SensorReadingService service() {
        return new SensorReadingService(moistureReadingRepository, batteryReadingRepository);
    }

    // --- record ---

    @Test
    void recordMoisture_savesReadingWithValuesAndCurrentTime() {
        UUID id = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        service().recordMoisture(id, 2, 42.5);

        ArgumentCaptor<MoistureReading> captor = ArgumentCaptor.forClass(MoistureReading.class);
        verify(moistureReadingRepository).save(captor.capture());
        MoistureReading saved = captor.getValue();
        assertEquals(id, saved.getInstanceId());
        assertEquals(2, saved.getSensorIndex());
        assertEquals(42.5, saved.getPercent());
        assertFalse(saved.getMeasuredAt().isBefore(before));
        assertFalse(saved.getMeasuredAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void recordBattery_savesReadingWithValuesAndCurrentTime() {
        UUID id = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        service().recordBattery(id, 78.1, 3.91);

        ArgumentCaptor<BatteryReading> captor = ArgumentCaptor.forClass(BatteryReading.class);
        verify(batteryReadingRepository).save(captor.capture());
        BatteryReading saved = captor.getValue();
        assertEquals(id, saved.getInstanceId());
        assertEquals(78.1, saved.getSoc());
        assertEquals(3.91, saved.getVoltage());
        assertFalse(saved.getMeasuredAt().isBefore(before));
        assertFalse(saved.getMeasuredAt().isAfter(OffsetDateTime.now()));
    }

    // --- query ---

    @Test
    void getMoisture_queriesSinceNowMinusRangeAndMapsToDto() {
        UUID id = UUID.randomUUID();
        OffsetDateTime measuredAt = OffsetDateTime.parse("2026-09-13T10:15:00Z");
        MoistureReading reading = new MoistureReading();
        reading.setInstanceId(id);
        reading.setSensorIndex((short) 1);
        reading.setPercent(55.0);
        reading.setMeasuredAt(measuredAt);
        when(moistureReadingRepository.findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(id), any()))
                .thenReturn(List.of(reading));

        OffsetDateTime before = OffsetDateTime.now();
        List<MoisturePoint> result = service().getMoisture(id, Duration.ofHours(24));
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(moistureReadingRepository).findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(id), since.capture());
        assertFalse(since.getValue().isBefore(before.minusHours(24)));
        assertFalse(since.getValue().isAfter(after.minusHours(24)));
        assertEquals(List.of(new MoisturePoint(measuredAt.toInstant(), 1, 55.0)), result);
    }

    @Test
    void getBattery_queriesSinceNowMinusRangeAndMapsToDto() {
        UUID id = UUID.randomUUID();
        OffsetDateTime measuredAt = OffsetDateTime.parse("2026-09-13T10:15:00Z");
        BatteryReading reading = new BatteryReading();
        reading.setInstanceId(id);
        reading.setSoc(78.1);
        reading.setVoltage(3.91);
        reading.setMeasuredAt(measuredAt);
        when(batteryReadingRepository.findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(id), any()))
                .thenReturn(List.of(reading));

        OffsetDateTime before = OffsetDateTime.now();
        List<BatteryPoint> result = service().getBattery(id, Duration.ofDays(7));
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(batteryReadingRepository).findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(id), since.capture());
        assertFalse(since.getValue().isBefore(before.minusDays(7)));
        assertFalse(since.getValue().isAfter(after.minusDays(7)));
        assertEquals(List.of(new BatteryPoint(measuredAt.toInstant(), 78.1, 3.91)), result);
    }
}
