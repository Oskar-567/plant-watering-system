package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.WateringEventResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.WateringEvent;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PumpService {

    private final InstanceRepository instanceRepository;
    private final WateringEventRepository wateringEventRepository;
    private final Optional<MqttPublisher> mqttPublisher;

    public PumpService(
            InstanceRepository instanceRepository,
            WateringEventRepository wateringEventRepository,
            Optional<MqttPublisher> mqttPublisher) {
        this.instanceRepository = instanceRepository;
        this.wateringEventRepository = wateringEventRepository;
        this.mqttPublisher = mqttPublisher;
    }

    public void start(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        mqttPublisher.ifPresent(p ->
                p.publish(instance.getMqttPrefix() + "/pump/command", "{\"action\":\"start\"}"));

        WateringEvent event = new WateringEvent();
        event.setInstanceId(instanceId);
        event.setStartedAt(OffsetDateTime.now());
        event.setTriggeredBy("app");
        wateringEventRepository.save(event);
    }

    public void stop(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        mqttPublisher.ifPresent(p ->
                p.publish(instance.getMqttPrefix() + "/pump/command", "{\"action\":\"stop\"}"));
    }

    public void recordFlowReceived(UUID instanceId, double liters) {
        wateringEventRepository.findByInstanceIdAndStoppedAtIsNull(instanceId)
                .ifPresent(event -> {
                    event.setStoppedAt(OffsetDateTime.now());
                    event.setLiters(BigDecimal.valueOf(liters));
                    wateringEventRepository.save(event);
                });
    }

    public List<WateringEventResponse> getHistory(UUID instanceId) {
        return wateringEventRepository.findByInstanceIdOrderByStartedAtDesc(instanceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WateringEventResponse toResponse(WateringEvent e) {
        Long durationSeconds = null;
        if (e.getStartedAt() != null && e.getStoppedAt() != null) {
            durationSeconds = ChronoUnit.SECONDS.between(e.getStartedAt(), e.getStoppedAt());
        }
        return new WateringEventResponse(
                e.getId(), e.getInstanceId(), e.getStartedAt(), e.getStoppedAt(),
                e.getLiters(), e.getTriggeredBy(), durationSeconds
        );
    }
}
