package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.WateringEventResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.WateringEvent;
import com.plant_watering_system.server.mqtt.MqttConnectedEvent;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class PumpService {

    private static final Logger log = LoggerFactory.getLogger(PumpService.class);

    public static final int MAX_DURATION_SECONDS = 600; // must match MAX_PUMP_RUNTIME_MS in firmware config.h

    public static final String OUTCOME_CANCELLED = "cancelled";
    public static final String OUTCOME_EXPIRED = "expired";
    public static final String OUTCOME_INTERRUPTED = "interrupted";

    private static final String TRIGGERED_BY_APP = "app";
    private static final String TRIGGERED_BY_SCHEDULE = "schedule";
    private static final String CLEARED_REQUEST = "{\"id\":null}";
    private static final Duration REQUEST_GRACE = Duration.ofMinutes(1);
    private static final Duration RUN_GRACE = Duration.ofMinutes(5);
    private static final Duration REPORT_SLACK = Duration.ofMinutes(1);

    private final InstanceRepository instanceRepository;
    private final WateringEventRepository wateringEventRepository;
    private final Optional<MqttPublisher> mqttPublisher;
    private final ScheduledExecutorService executor;
    private final Duration requestTtl;

    // Serializes every event mutation (HTTP, MQTT callback and scheduler threads). Held for DB work
    // only, never across an MQTT publish: Paho's callback thread may be waiting for it, and a
    // synchronous QoS 1 publish needs that thread. Single pod (Recreate), so a JVM lock suffices.
    private final ReentrantLock lock = new ReentrantLock();

    public PumpService(
            InstanceRepository instanceRepository,
            WateringEventRepository wateringEventRepository,
            Optional<MqttPublisher> mqttPublisher,
            ScheduledExecutorService executor,
            @Value("${pump.request-ttl:20m}") Duration requestTtl) {
        this.instanceRepository = instanceRepository;
        this.wateringEventRepository = wateringEventRepository;
        this.mqttPublisher = mqttPublisher;
        this.executor = executor;
        this.requestTtl = requestTtl;
    }

    /**
     * Requests a manual run. The ESP32 sleeps between wakes, so the request is
     * retained on the broker and executed once, on the device's next wake.
     * A newer request replaces an open one.
     */
    public void start(UUID instanceId, int durationSeconds) {
        Instance instance = requireInstance(instanceId);
        locked(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            cancelOpenRequests(instanceId, now);

            WateringEvent request = newEvent(instanceId, TRIGGERED_BY_APP);
            request.setRequestedAt(now);
            request.setRequestedDurationSeconds(durationSeconds);
            request.setExpiresAt(now.plus(requestTtl));
            wateringEventRepository.save(request);
        });

        offloadSync(instanceId);
        // Legacy firmware (phase 4 removes this): runs it at once; new firmware ignores "start"
        mqttPublisher.ifPresent(p -> p.publish(
                instance.getMqttPrefix() + "/pump/command",
                "{\"action\":\"start\",\"duration_s\":" + durationSeconds + "}"));
    }

    /** Cancels an open request and stops a running pump. */
    public void stop(UUID instanceId) {
        Instance instance = requireInstance(instanceId);
        boolean cancelled = lockedGet(() -> cancelOpenRequests(instanceId, OffsetDateTime.now()));
        if (cancelled) offloadSync(instanceId);
        // Also after a cancel: the device may have picked the request up meanwhile
        mqttPublisher.ifPresent(p ->
                p.publish(instance.getMqttPrefix() + "/pump/command", "{\"action\":\"stop\"}"));
    }

    /**
     * Makes the retained request match the database: the open request if there
     * is one, else cleared. Idempotent -- the device runs each id at most once.
     * Runs on the single executor thread only (see offloadSync), so syncs never overtake each
     * other: the last one reads the latest state. Never on Paho's callback thread.
     */
    public void syncRetainedRequest(UUID instanceId) {
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            String payload = lockedGet(() -> wateringEventRepository
                    .findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(instanceId)
                    .stream()
                    .max(Comparator.comparing(WateringEvent::getRequestedAt))
                    .map(PumpService::toRequestPayload)
                    .orElse(CLEARED_REQUEST));
            mqttPublisher.ifPresent(p -> p.publishRetained(instance.getMqttPrefix() + "/pump/request", payload));
        });
    }

    // Retained messages are lost if the broker restarts without persistence
    public void republishAll() {
        instanceRepository.findAll().forEach(i -> syncRetainedRequest(i.getId()));
    }

    // ApplicationReadyEvent too: the first connect happens in MqttClientManager's
    // @PostConstruct, before @EventListener beans are registered.
    @EventListener({ApplicationReadyEvent.class, MqttConnectedEvent.class})
    public void onMqttAvailable() {
        executor.execute(this::republishAll);
    }

    // Flow arrives right before the matching "off" -- it only fills in liters, "off" closes the event
    public void recordFlowReceived(UUID instanceId, double liters, String trigger, String requestId) {
        locked(() -> findTarget(instanceId, trigger, requestId)
                .or(() -> requestId == null
                        ? findInterrupted(instanceId, trigger).filter(e -> e.getLiters() == null)
                        : Optional.empty())
                .ifPresent(event -> {
                    event.setLiters(BigDecimal.valueOf(liters));
                    wateringEventRepository.save(event);
                }));
    }

    /**
     * Applies a plant/status pump report. Request runs carry the event id;
     * schedule runs and legacy firmware don't, and match the newest open event
     * of their trigger. started_at is set only by "on": it means the pump ran.
     *
     * @param pump      "on", "off" or "rejected"
     * @param trigger   "manual", "schedule" or null (old firmware = manual)
     * @param reason    outcome reported by the firmware, may be null
     * @param ts        device epoch seconds, 0 = unknown (use receive time)
     * @param requestId event id of a request run, null otherwise
     */
    public void recordPumpStatus(UUID instanceId, String pump, String trigger, String reason, long ts,
                                 String requestId) {
        locked(() -> applyPumpStatus(instanceId, pump, trigger, reason, ts, requestId));
    }

    private void applyPumpStatus(UUID instanceId, String pump, String trigger, String reason, long ts,
                                 String requestId) {
        OffsetDateTime at = ts > 0
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC)
                : OffsetDateTime.now();
        String triggeredBy = toTriggeredBy(trigger);

        if ("on".equals(pump)) {
            if (requestId == null && TRIGGERED_BY_SCHEDULE.equals(triggeredBy)) {
                WateringEvent event = newEvent(instanceId, triggeredBy);
                event.setStartedAt(at);
                wateringEventRepository.save(event);
                return;
            }
            findTarget(instanceId, trigger, requestId).ifPresent(event -> markStarted(event, at));
            return;
        }
        if (!"off".equals(pump) && !"rejected".equals(pump)) return;

        Optional<WateringEvent> target = findTarget(instanceId, trigger, requestId);
        if (target.isEmpty() && requestId == null && "off".equals(pump)) {
            // A queued "off" after a long outage: the safety net already closed the run as interrupted.
            // Only if the reported stop fits that run -- the device stops by itself after its duration.
            target = findInterrupted(instanceId, trigger).filter(e -> fitsRun(e, at));
        }
        if (target.isPresent()) {
            WateringEvent event = target.get();
            close(event, at, reason);
            if ("rejected".equals(pump) && event.getRequestedAt() != null) offloadSync(instanceId);
        } else if (requestId == null && TRIGGERED_BY_SCHEDULE.equals(triggeredBy)) {
            // Missed/refused schedule slot: never sent "on", logged as a closed event that never ran
            close(newEvent(instanceId, triggeredBy), at, reason);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeStale() {
        closeStale(OffsetDateTime.now());
    }

    /**
     * Safety net for reports that never arrive: unanswered requests expire,
     * and runs whose "off" was lost (reset before it could be sent) are closed.
     */
    void closeStale(OffsetDateTime now) {
        Set<UUID> requestsChanged = lockedGet(() -> {
            Set<UUID> changed = new HashSet<>();
            for (WateringEvent e : wateringEventRepository.findByStoppedAtIsNull()) {
                if (e.getStartedAt() == null) {
                    if (e.getExpiresAt() != null && now.isAfter(e.getExpiresAt().plus(REQUEST_GRACE))) {
                        close(e, now, OUTCOME_EXPIRED);
                        changed.add(e.getInstanceId());
                    }
                } else {
                    OffsetDateTime plannedEnd = plannedEnd(e);
                    if (now.isAfter(plannedEnd.plus(RUN_GRACE))) {
                        // The firmware stops by itself at the planned end -- the honest upper bound
                        close(e, plannedEnd.isBefore(now) ? plannedEnd : now, OUTCOME_INTERRUPTED);
                    }
                }
            }
            return changed;
        });
        requestsChanged.forEach(this::offloadSync);
    }

    public List<WateringEventResponse> getHistory(UUID instanceId) {
        return wateringEventRepository.findHistory(instanceId)
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

    private static WateringEvent newEvent(UUID instanceId, String triggeredBy) {
        WateringEvent event = new WateringEvent();
        event.setInstanceId(instanceId);
        event.setTriggeredBy(triggeredBy);
        return event;
    }

    private void markStarted(WateringEvent event, OffsetDateTime at) {
        if (event.getStartedAt() != null) return;    // QoS 1 redelivery
        event.setStartedAt(at);
        if (OUTCOME_EXPIRED.equals(event.getOutcome())) {
            // The server gave up, but the device did start it (clock skew): it ran
            event.setStoppedAt(null);
            event.setOutcome(null);
        }
        wateringEventRepository.save(event);
        if (event.getRequestedAt() != null) offloadSync(event.getInstanceId());
    }

    // An id that is unknown or belongs to another instance is ignored -- never guessed
    private Optional<WateringEvent> findTarget(UUID instanceId, String trigger, String requestId) {
        if (requestId != null) {
            try {
                return wateringEventRepository.findById(UUID.fromString(requestId))
                        .filter(e -> instanceId.equals(e.getInstanceId()))
                        .or(() -> {
                            log.warn("Ignoring report for unknown request {} of instance {}", requestId, instanceId);
                            return Optional.empty();
                        });
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring report with malformed request id {}", requestId);
                return Optional.empty();
            }
        }
        return wateringEventRepository.findOpenByTrigger(instanceId, toTriggeredBy(trigger)).stream().findFirst();
    }

    private Optional<WateringEvent> findInterrupted(UUID instanceId, String trigger) {
        return wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndOutcomeOrderByStartedAtDesc(
                instanceId, toTriggeredBy(trigger), OUTCOME_INTERRUPTED);
    }

    // The reported stop lies within the run: after its start, not after its planned end (+ slack)
    private static boolean fitsRun(WateringEvent e, OffsetDateTime stoppedAt) {
        return e.getStartedAt() != null
                && !stoppedAt.isBefore(e.getStartedAt())
                && !stoppedAt.isAfter(plannedEnd(e).plus(REPORT_SLACK));
    }

    private static OffsetDateTime plannedEnd(WateringEvent e) {
        int expectedS = e.getRequestedDurationSeconds() != null
                ? e.getRequestedDurationSeconds() : MAX_DURATION_SECONDS;
        return e.getStartedAt().plusSeconds(expectedS);
    }

    // All retained-request syncs go through the single executor thread, in order. Also keeps
    // publishing off Paho's callback thread, where a synchronous QoS 1 publish deadlocks.
    private void offloadSync(UUID instanceId) {
        executor.execute(() -> syncRetainedRequest(instanceId));
    }

    private void locked(Runnable work) {
        lock.lock();
        try {
            work.run();
        } finally {
            lock.unlock();
        }
    }

    private <T> T lockedGet(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    private boolean cancelOpenRequests(UUID instanceId, OffsetDateTime at) {
        List<WateringEvent> open = wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(instanceId);
        open.forEach(e -> close(e, at, OUTCOME_CANCELLED));
        return !open.isEmpty();
    }

    private static String toRequestPayload(WateringEvent e) {
        return "{\"id\":\"" + e.getId() + "\",\"duration_s\":" + e.getRequestedDurationSeconds()
                + ",\"expires\":" + e.getExpiresAt().toEpochSecond() + "}";
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
                e.getId(), e.getInstanceId(), e.getRequestedAt(), e.getRequestedDurationSeconds(),
                e.getExpiresAt(), e.getStartedAt(), e.getStoppedAt(), e.getLiters(), e.getTriggeredBy(), e.getOutcome(),
                durationSeconds
        );
    }
}
