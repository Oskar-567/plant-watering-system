package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.WateringEventResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.WateringEvent;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PumpServiceTest {

    @Mock InstanceRepository instanceRepository;
    @Mock WateringEventRepository wateringEventRepository;
    @Mock MqttPublisher mqttPublisher;

    private PumpService serviceWithMqtt() {
        return new PumpService(instanceRepository, wateringEventRepository, Optional.of(mqttPublisher));
    }

    private PumpService serviceWithoutMqtt() {
        return new PumpService(instanceRepository, wateringEventRepository, Optional.empty());
    }

    private Instance instanceWithPrefix(String prefix) {
        Instance i = new Instance();
        i.setMqttPrefix(prefix);
        return i;
    }

    private WateringEvent openEvent(UUID instanceId, String triggeredBy) {
        WateringEvent e = new WateringEvent();
        e.setInstanceId(instanceId);
        e.setStartedAt(OffsetDateTime.now().minusSeconds(30));
        e.setTriggeredBy(triggeredBy);
        return e;
    }

    // --- start ---

    @Test
    void start_publishesMqttCommandWithDurationAndSavesEvent() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));

        serviceWithMqtt().start(id, 600);

        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"start\",\"duration_s\":600}");

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals(id, saved.getInstanceId());
        assertEquals("app", saved.getTriggeredBy());
        assertNotNull(saved.getStartedAt());
    }

    @Test
    void start_withMqttDisabled_stillSavesEvent() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));

        serviceWithoutMqtt().start(id, 600);

        verify(wateringEventRepository).save(any(WateringEvent.class));
        verifyNoInteractions(mqttPublisher);
    }

    @Test
    void start_unknownInstance_throws404() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> serviceWithMqtt().start(id, 600));
        verify(wateringEventRepository, never()).save(any());
    }

    // --- stop ---

    @Test
    void stop_publishesMqttStopCommand() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));

        serviceWithMqtt().stop(id);

        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"stop\"}");
    }

    @Test
    void stop_unknownInstance_throws404() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> serviceWithMqtt().stop(id));
    }

    // --- recordFlowReceived ---

    @Test
    void recordFlowReceived_setsLitersOnOpenEventWithoutClosingIt() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "schedule");
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "schedule"))
                .thenReturn(Optional.of(open));

        serviceWithMqtt().recordFlowReceived(id, 0.35, "schedule");

        verify(wateringEventRepository).save(open);
        assertEquals(BigDecimal.valueOf(0.35), open.getLiters());
        assertNull(open.getStoppedAt());
    }

    @Test
    void recordFlowReceived_withoutTrigger_usesManualEvent() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.of(open));

        serviceWithMqtt().recordFlowReceived(id, 0.35, null);

        assertEquals(BigDecimal.valueOf(0.35), open.getLiters());
    }

    @Test
    void recordFlowReceived_noOpenEvent_doesNothing() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.empty());

        serviceWithMqtt().recordFlowReceived(id, 0.35, "manual");

        verify(wateringEventRepository, never()).save(any());
    }

    // --- recordPumpStatus ---

    private static final long TS = 1757764800L;

    @Test
    void recordPumpStatus_offManual_closesOpenAppEventWithOutcome() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.of(open));

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS);

        verify(wateringEventRepository).save(open);
        assertEquals("completed", open.getOutcome());
        assertEquals(TS, open.getStoppedAt().toEpochSecond());
    }

    @Test
    void recordPumpStatus_withoutTimestamp_usesReceiveTime() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.of(open));

        serviceWithMqtt().recordPumpStatus(id, "off", null, null, 0L);

        assertNotNull(open.getStoppedAt());
        assertTrue(open.getStoppedAt().isAfter(OffsetDateTime.now().minusSeconds(5)));
        assertNull(open.getOutcome());
    }

    @Test
    void recordPumpStatus_rejectedBusy_closesOpenAppEvent() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.of(open));

        serviceWithMqtt().recordPumpStatus(id, "rejected", "manual", "busy", TS);

        assertEquals("busy", open.getOutcome());
        assertNotNull(open.getStoppedAt());
    }

    @Test
    void recordPumpStatus_onSchedule_createsOpenScheduleEvent() {
        UUID id = UUID.randomUUID();

        serviceWithMqtt().recordPumpStatus(id, "on", "schedule", null, TS);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals(id, saved.getInstanceId());
        assertEquals("schedule", saved.getTriggeredBy());
        assertEquals(TS, saved.getStartedAt().toEpochSecond());
        assertNull(saved.getStoppedAt());
    }

    @Test
    void recordPumpStatus_onManual_createsNothing() {
        serviceWithMqtt().recordPumpStatus(UUID.randomUUID(), "on", "manual", null, TS);

        verify(wateringEventRepository, never()).save(any());
    }

    @Test
    void recordPumpStatus_missedScheduleWithoutOpenEvent_createsClosedEvent() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "schedule"))
                .thenReturn(Optional.empty());

        serviceWithMqtt().recordPumpStatus(id, "rejected", "schedule", "missed", TS);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals("schedule", saved.getTriggeredBy());
        assertEquals("missed", saved.getOutcome());
        assertEquals(TS, saved.getStartedAt().toEpochSecond());
        assertEquals(TS, saved.getStoppedAt().toEpochSecond());
    }

    @Test
    void recordPumpStatus_offManualWithoutOpenEvent_doesNothing() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(id, "app"))
                .thenReturn(Optional.empty());

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "command", TS);

        verify(wateringEventRepository, never()).save(any());
    }

    // --- getHistory ---

    @Test
    void getHistory_returnsMappedResponsesWithDuration() {
        UUID id = UUID.randomUUID();
        WateringEvent event = new WateringEvent();
        event.setInstanceId(id);
        event.setStartedAt(OffsetDateTime.now().minusSeconds(60));
        event.setStoppedAt(OffsetDateTime.now());
        event.setLiters(BigDecimal.valueOf(1.5));
        event.setTriggeredBy("app");
        event.setOutcome("completed");

        when(wateringEventRepository.findByInstanceIdOrderByStartedAtDesc(id))
                .thenReturn(List.of(event));

        List<WateringEventResponse> history = serviceWithMqtt().getHistory(id);

        assertEquals(1, history.size());
        WateringEventResponse response = history.get(0);
        assertEquals(BigDecimal.valueOf(1.5), response.liters());
        assertNotNull(response.durationSeconds());
        assertTrue(response.durationSeconds() >= 59);
        assertEquals("completed", response.outcome());
    }

    @Test
    void getHistory_openEvent_hasDurationNull() {
        UUID id = UUID.randomUUID();
        WateringEvent event = new WateringEvent();
        event.setInstanceId(id);
        event.setStartedAt(OffsetDateTime.now());

        when(wateringEventRepository.findByInstanceIdOrderByStartedAtDesc(id))
                .thenReturn(List.of(event));

        List<WateringEventResponse> history = serviceWithMqtt().getHistory(id);

        assertNull(history.get(0).durationSeconds());
    }
}
