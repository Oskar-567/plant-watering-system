package com.plant_watering_system.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plant_watering_system.server.dto.ScheduleEntryResponse;
import com.plant_watering_system.server.dto.ScheduleRequest;
import com.plant_watering_system.server.dto.ScheduleResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.ScheduleEntry;
import com.plant_watering_system.server.model.WateringSchedule;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

@Service
public class ScheduleService {

    public static final int MAX_ENTRIES = 8; // must match SCHEDULE_MAX_ENTRIES in firmware schedule_model.h

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final InstanceRepository instanceRepository;
    private final WateringScheduleRepository scheduleRepository;
    private final Optional<MqttPublisher> mqttPublisher;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executor;
    private final String posixTimezone;

    public ScheduleService(
            InstanceRepository instanceRepository,
            WateringScheduleRepository scheduleRepository,
            Optional<MqttPublisher> mqttPublisher,
            ObjectMapper objectMapper,
            ScheduledExecutorService executor,
            @Value("${schedule.posix-tz}") String posixTimezone) {
        this.instanceRepository = instanceRepository;
        this.scheduleRepository = scheduleRepository;
        this.mqttPublisher = mqttPublisher;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.posixTimezone = posixTimezone;
    }

    public ScheduleResponse get(UUID instanceId) {
        requireInstance(instanceId);
        return scheduleRepository.findById(instanceId)
                .map(ScheduleService::toResponse)
                .orElse(new ScheduleResponse(0, null, true, null, List.of()));
    }

    public ScheduleResponse replace(UUID instanceId, ScheduleRequest request) {
        Instance instance = requireInstance(instanceId);
        WateringSchedule schedule = scheduleRepository.findById(instanceId).orElseGet(() -> {
            WateringSchedule created = new WateringSchedule();
            created.setInstanceId(instanceId);
            return created;
        });

        schedule.setVersion(schedule.getVersion() + 1);
        schedule.setUpdatedAt(OffsetDateTime.now());
        schedule.getEntries().clear();
        request.entries().forEach(e -> schedule.getEntries().add(new ScheduleEntry(
                LocalTime.parse(e.time()), e.durationSeconds(), toDaysMask(e.days()))));

        WateringSchedule saved = scheduleRepository.save(schedule);
        publish(instance.getMqttPrefix(), saved);
        return toResponse(saved);
    }

    public void recordAck(UUID instanceId, int version) {
        scheduleRepository.findById(instanceId).ifPresent(schedule -> {
            schedule.setAcknowledgedVersion(version);
            scheduleRepository.save(schedule);
        });
    }

    // Retained messages are lost if the broker restarts without persistence,
    // so every schedule is published again whenever the MQTT link (re)appears.
    public void republishAll() {
        for (WateringSchedule schedule : scheduleRepository.findAll()) {
            instanceRepository.findById(schedule.getInstanceId())
                    .ifPresent(instance -> publish(instance.getMqttPrefix(), schedule));
        }
    }

    public void onMqttAvailable() {
        // Never publish on Paho's callback thread (connectComplete): a synchronous
        // QoS 1 publish waits for an ack that this very thread would have to process.
        executor.execute(this::republishAll);
    }

    private Instance requireInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void publish(String mqttPrefix, WateringSchedule schedule) {
        mqttPublisher.ifPresent(p -> p.publishRetained(mqttPrefix + "/schedule", toMqttPayload(schedule)));
    }

    // Compact keys -- the ESP32 parses this with a 1024-byte MQTT buffer
    record MqttScheduleEntry(String t, int d, int w) {}
    record MqttSchedulePayload(int version, String tz, List<MqttScheduleEntry> entries) {}

    private String toMqttPayload(WateringSchedule schedule) {
        List<MqttScheduleEntry> entries = schedule.getEntries().stream()
                .map(e -> new MqttScheduleEntry(e.getTimeOfDay().format(HH_MM), e.getDurationSeconds(), e.getDaysMask()))
                .toList();
        try {
            return objectMapper.writeValueAsString(new MqttSchedulePayload(schedule.getVersion(), posixTimezone, entries));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize schedule", e);
        }
    }

    static int toDaysMask(Set<DayOfWeek> days) {
        return days.stream().mapToInt(d -> 1 << (d.getValue() - 1)).reduce(0, (a, b) -> a | b);
    }

    static List<DayOfWeek> fromDaysMask(int mask) {
        return Arrays.stream(DayOfWeek.values())
                .filter(d -> (mask & (1 << (d.getValue() - 1))) != 0)
                .toList();
    }

    private static ScheduleResponse toResponse(WateringSchedule s) {
        List<ScheduleEntryResponse> entries = s.getEntries().stream()
                .map(e -> new ScheduleEntryResponse(
                        e.getTimeOfDay().format(HH_MM), e.getDurationSeconds(), fromDaysMask(e.getDaysMask())))
                .toList();
        boolean synced = s.getAcknowledgedVersion() != null && s.getAcknowledgedVersion() == s.getVersion();
        return new ScheduleResponse(s.getVersion(), s.getAcknowledgedVersion(), synced, s.getUpdatedAt(), entries);
    }
}
