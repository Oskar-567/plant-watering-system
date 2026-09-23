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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PumpService {

    public static final int MAX_DURATION_SECONDS = 600; // must match MAX_PUMP_RUNTIME_MS in firmware config.h

    private static final String TRIGGERED_BY_APP = "app";
    private static final String TRIGGERED_BY_SCHEDULE = "schedule";

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

    public void start(UUID instanceId, int durationSeconds) {
        Instance instance = requireInstance(instanceId);

        mqttPublisher.ifPresent(p -> p.publish(
                instance.getMqttPrefix() + "/pump/command",
                "{\"action\":\"start\",\"duration_s\":" + durationSeconds + "}"));

        wateringEventRepository.save(newEvent(instanceId, OffsetDateTime.now(), TRIGGERED_BY_APP));
    }

    public void stop(UUID instanceId) {
        Instance instance = requireInstance(instanceId);

        mqttPublisher.ifPresent(p ->
                p.publish(instance.getMqttPrefix() + "/pump/command", "{\"action\":\"stop\"}"));
    }

    // Flow arrives right before the matching "off" -- it only fills in liters, "off" closes the event
    public void recordFlowReceived(UUID instanceId, double liters, String trigger) {
        findOpenEvent(instanceId, toTriggeredBy(trigger)).ifPresent(event -> {
            event.setLiters(BigDecimal.valueOf(liters));
            wateringEventRepository.save(event);
        });
    }

    /**
     * Applies a plant/status pump report. Manual runs already have an open event
     * (created by start()); schedule runs are started by the ESP32 itself, so the
     * server first learns about them here. Missed/refused schedule runs never
     * sent "on" and are logged as closed events.
     *
     * @param pump    "on", "off" or "rejected"
     * @param trigger "manual", "schedule" or null (old firmware = manual)
     * @param reason  outcome reported by the firmware, may be null
     * @param ts      device epoch seconds, 0 = unknown (use receive time)
     */
    public void recordPumpStatus(UUID instanceId, String pump, String trigger, String reason, long ts) {
        OffsetDateTime at = ts > 0
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC)
                : OffsetDateTime.now();
        String triggeredBy = toTriggeredBy(trigger);

        if ("on".equals(pump)) {
            if (TRIGGERED_BY_SCHEDULE.equals(triggeredBy)) {
                wateringEventRepository.save(newEvent(instanceId, at, triggeredBy));
            }
            return;
        }
        if (!"off".equals(pump) && !"rejected".equals(pump)) return;

        Optional<WateringEvent> open = findOpenEvent(instanceId, triggeredBy);
        if (open.isPresent()) {
            close(open.get(), at, reason);
        } else if (TRIGGERED_BY_SCHEDULE.equals(triggeredBy)) {
            close(newEvent(instanceId, at, triggeredBy), at, reason);
        }
    }

    public List<WateringEventResponse> getHistory(UUID instanceId) {
        return wateringEventRepository.findByInstanceIdOrderByStartedAtDesc(instanceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Firmware reports "manual"/"schedule"; events store "app"/"schedule"
    static String toTriggeredBy(String firmwareTrigger) {
        return TRIGGERED_BY_SCHEDULE.equals(firmwareTrigger) ? TRIGGERED_BY_SCHEDULE : TRIGGERED_BY_APP;
    }

    private Instance requireInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Optional<WateringEvent> findOpenEvent(UUID instanceId, String triggeredBy) {
        return wateringEventRepository
                .findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(instanceId, triggeredBy);
    }

    private static WateringEvent newEvent(UUID instanceId, OffsetDateTime startedAt, String triggeredBy) {
        WateringEvent event = new WateringEvent();
        event.setInstanceId(instanceId);
        event.setStartedAt(startedAt);
        event.setTriggeredBy(triggeredBy);
        return event;
    }

    private void close(WateringEvent event, OffsetDateTime at, String reason) {
        event.setStoppedAt(at);
        event.setOutcome(reason);
        wateringEventRepository.save(event);
    }

    private WateringEventResponse toResponse(WateringEvent e) {
        Long durationSeconds = null;
        if (e.getStartedAt() != null && e.getStoppedAt() != null) {
            durationSeconds = ChronoUnit.SECONDS.between(e.getStartedAt(), e.getStoppedAt());
        }
        return new WateringEventResponse(
                e.getId(), e.getInstanceId(), e.getStartedAt(), e.getStoppedAt(),
                e.getLiters(), e.getTriggeredBy(), e.getOutcome(), durationSeconds
        );
    }
}
