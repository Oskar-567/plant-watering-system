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

    // --- start ---

    @Test
    void start_publishesMqttCommandAndSavesEvent() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));

        serviceWithMqtt().start(id);

        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"start\"}");

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

        serviceWithoutMqtt().start(id);

        verify(wateringEventRepository).save(any(WateringEvent.class));
        verifyNoInteractions(mqttPublisher);
    }

    @Test
    void start_unknownInstance_throws404() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> serviceWithMqtt().start(id));
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
    void recordFlowReceived_closesOpenEvent() {
        UUID id = UUID.randomUUID();
        WateringEvent openEvent = new WateringEvent();
        openEvent.setInstanceId(id);
        openEvent.setStartedAt(OffsetDateTime.now().minusSeconds(30));

        when(wateringEventRepository.findByInstanceIdAndStoppedAtIsNull(id))
                .thenReturn(Optional.of(openEvent));

        serviceWithMqtt().recordFlowReceived(id, 0.35);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertNotNull(saved.getStoppedAt());
        assertEquals(BigDecimal.valueOf(0.35), saved.getLiters());
    }

    @Test
    void recordFlowReceived_noOpenEvent_doesNothing() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findByInstanceIdAndStoppedAtIsNull(id))
                .thenReturn(Optional.empty());

        serviceWithMqtt().recordFlowReceived(id, 0.35);

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

        when(wateringEventRepository.findByInstanceIdOrderByStartedAtDesc(id))
                .thenReturn(List.of(event));

        List<WateringEventResponse> history = serviceWithMqtt().getHistory(id);

        assertEquals(1, history.size());
        WateringEventResponse response = history.get(0);
        assertEquals(BigDecimal.valueOf(1.5), response.liters());
        assertNotNull(response.durationSeconds());
        assertTrue(response.durationSeconds() >= 59);
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
