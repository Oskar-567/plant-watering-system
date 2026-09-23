package com.plant_watering_system.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plant_watering_system.server.dto.ScheduleEntryRequest;
import com.plant_watering_system.server.dto.ScheduleRequest;
import com.plant_watering_system.server.dto.ScheduleResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.ScheduleEntry;
import com.plant_watering_system.server.model.WateringSchedule;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final String TZ = "CET-1CEST,M3.5.0,M10.5.0/3";

    @Mock InstanceRepository instanceRepository;
    @Mock WateringScheduleRepository scheduleRepository;
    @Mock MqttPublisher mqttPublisher;
    @Mock ScheduledExecutorService executor;

    private final UUID id = UUID.randomUUID();

    private ScheduleService service() {
        return new ScheduleService(instanceRepository, scheduleRepository, Optional.of(mqttPublisher),
                new ObjectMapper(), executor, TZ);
    }

    private void givenInstance() {
        Instance instance = new Instance();
        instance.setMqttPrefix("plant");
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instance));
    }

    private WateringSchedule storedSchedule(int version, Integer ack) {
        WateringSchedule s = new WateringSchedule();
        s.setInstanceId(id);
        s.setVersion(version);
        s.setAcknowledgedVersion(ack);
        s.setUpdatedAt(OffsetDateTime.now());
        s.getEntries().add(new ScheduleEntry(LocalTime.of(12, 0), 600, 21));
        return s;
    }

    @Test
    void get_withoutSchedule_returnsEmptyVersionZero() {
        givenInstance();
        when(scheduleRepository.findById(id)).thenReturn(Optional.empty());

        ScheduleResponse response = service().get(id);

        assertEquals(0, response.version());
        assertTrue(response.synced());
        assertTrue(response.entries().isEmpty());
    }

    @Test
    void get_mapsEntriesAndSyncState() {
        givenInstance();
        when(scheduleRepository.findById(id)).thenReturn(Optional.of(storedSchedule(3, 2)));

        ScheduleResponse response = service().get(id);

        assertEquals(3, response.version());
        assertEquals(2, response.acknowledgedVersion());
        assertFalse(response.synced());
        assertEquals("12:00", response.entries().get(0).time());
        assertEquals(600, response.entries().get(0).durationSeconds());
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), response.entries().get(0).days());
    }

    @Test
    void get_unknownInstance_throws404() {
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service().get(id));
    }

    @Test
    void replace_firstSchedule_savesVersionOneAndPublishesRetainedPayload() {
        givenInstance();
        when(scheduleRepository.findById(id)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = service().replace(id, new ScheduleRequest(List.of(
                new ScheduleEntryRequest("12:00", 600, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                new ScheduleEntryRequest("20:30", 300, Set.of(DayOfWeek.SUNDAY)))));

        assertEquals(1, response.version());
        assertFalse(response.synced());
        verify(mqttPublisher).publishRetained("plant/schedule",
                "{\"version\":1,\"tz\":\"" + TZ + "\",\"entries\":["
                        + "{\"t\":\"12:00\",\"d\":600,\"w\":21},"
                        + "{\"t\":\"20:30\",\"d\":300,\"w\":64}]}");
    }

    @Test
    void replace_existingSchedule_incrementsVersionAndReplacesEntries() {
        givenInstance();
        when(scheduleRepository.findById(id)).thenReturn(Optional.of(storedSchedule(3, 3)));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = service().replace(id, new ScheduleRequest(List.of()));

        assertEquals(4, response.version());
        assertTrue(response.entries().isEmpty());
        verify(mqttPublisher).publishRetained("plant/schedule",
                "{\"version\":4,\"tz\":\"" + TZ + "\",\"entries\":[]}");
    }

    @Test
    void recordAck_storesAcknowledgedVersion() {
        WateringSchedule stored = storedSchedule(5, 4);
        when(scheduleRepository.findById(id)).thenReturn(Optional.of(stored));

        service().recordAck(id, 5);

        assertEquals(5, stored.getAcknowledgedVersion());
        verify(scheduleRepository).save(stored);
    }

    @Test
    void republishAll_publishesEverySchedule() {
        givenInstance();
        when(scheduleRepository.findAll()).thenReturn(List.of(storedSchedule(2, 2)));

        service().republishAll();

        verify(mqttPublisher).publishRetained("plant/schedule",
                "{\"version\":2,\"tz\":\"" + TZ + "\",\"entries\":[{\"t\":\"12:00\",\"d\":600,\"w\":21}]}");
    }

    @Test
    void onMqttAvailable_republishesOnExecutorThread() {
        when(scheduleRepository.findAll()).thenReturn(List.of());
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(executor).execute(any());

        service().onMqttAvailable();

        verify(executor).execute(any());
        verify(scheduleRepository).findAll();
    }
}
