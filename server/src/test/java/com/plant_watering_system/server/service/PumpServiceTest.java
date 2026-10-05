package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.WateringEventResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.model.WateringEvent;
import com.plant_watering_system.server.mqtt.MqttPublisher;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PumpServiceTest {

    @Mock InstanceRepository instanceRepository;
    @Mock WateringEventRepository wateringEventRepository;
    @Mock MqttPublisher mqttPublisher;
    @Mock ScheduledExecutorService executor;

    private static final Duration TTL = Duration.ofMinutes(20);

    // Offloaded work runs inline, so tests see its effects synchronously
    @BeforeEach
    void runOffloadedWorkInline() {
        lenient().doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(executor).execute(any(Runnable.class));
    }

    private PumpService serviceWithMqtt() {
        return new PumpService(instanceRepository, wateringEventRepository, Optional.of(mqttPublisher), executor, TTL);
    }

    private PumpService serviceWithoutMqtt() {
        return new PumpService(instanceRepository, wateringEventRepository, Optional.empty(), executor, TTL);
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

    // Has an id like a persisted row (the entity has no setId)
    private WateringEvent requestedEvent(UUID instanceId, UUID eventId, OffsetDateTime expiresAt) {
        WateringEvent e = spy(new WateringEvent());
        lenient().when(e.getId()).thenReturn(eventId);
        e.setInstanceId(instanceId);
        e.setTriggeredBy("app");
        e.setRequestedAt(expiresAt.minus(TTL));
        e.setRequestedDurationSeconds(120);
        e.setExpiresAt(expiresAt);
        return e;
    }

    // --- start ---

    @Test
    void start_savesRequestedEventAndPublishesRetainedRequestPlusLegacyStart() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent published = requestedEvent(id, eventId, OffsetDateTime.now().plus(TTL));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id))
                .thenReturn(List.of(), List.of(published));   // nothing to replace, then the new request
        OffsetDateTime before = OffsetDateTime.now();

        serviceWithMqtt().start(id, 120);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals("app", saved.getTriggeredBy());
        assertEquals(120, saved.getRequestedDurationSeconds());
        assertNull(saved.getStartedAt());
        assertFalse(saved.getRequestedAt().isBefore(before));
        assertEquals(TTL, Duration.between(saved.getRequestedAt(), saved.getExpiresAt()));

        verify(mqttPublisher).publishRetained(eq("plant/pump/request"),
                matches("\\{\"id\":\"" + eventId + "\",\"duration_s\":120,\"expires\":\\d+}"));
        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"start\",\"duration_s\":120}");
    }

    @Test
    void start_replacesOpenRequestByCancellingIt() {
        UUID id = UUID.randomUUID();
        WateringEvent old = requestedEvent(id, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(5));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id))
                .thenReturn(List.of(old), List.of());

        serviceWithMqtt().start(id, 60);

        assertEquals("cancelled", old.getOutcome());
        assertNotNull(old.getStoppedAt());
        verify(wateringEventRepository).save(old);
    }

    @Test
    void start_withMqttDisabled_stillSavesRequest() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

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
    void stop_withoutOpenRequest_publishesStopCommand() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().stop(id);

        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"stop\"}");
        verify(mqttPublisher, never()).publishRetained(any(), any());
    }

    @Test
    void stop_withOpenRequest_cancelsItClearsRetainedAndStillSendsStop() {
        UUID id = UUID.randomUUID();
        WateringEvent open = requestedEvent(id, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(5));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id))
                .thenReturn(List.of(open), List.of());

        serviceWithMqtt().stop(id);

        assertEquals("cancelled", open.getOutcome());
        assertNotNull(open.getStoppedAt());
        verify(mqttPublisher).publishRetained("plant/pump/request", "{\"id\":null}");
        // The device may have woken and started it in the meantime
        verify(mqttPublisher).publish("plant/pump/command", "{\"action\":\"stop\"}");
    }

    @Test
    void stop_unknownInstance_throws404() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> serviceWithMqtt().stop(id));
    }

    // --- retained request ---

    @Test
    void syncRetainedRequest_withNewerOpenRequest_publishesItInsteadOfClearing() {
        UUID id = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        WateringEvent newerRequest = requestedEvent(id, newer, OffsetDateTime.now().plusMinutes(10));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id))
                .thenReturn(List.of(newerRequest));

        serviceWithMqtt().syncRetainedRequest(id);

        verify(mqttPublisher).publishRetained(eq("plant/pump/request"), contains(newer.toString()));
        verify(mqttPublisher, never()).publishRetained("plant/pump/request", "{\"id\":null}");
    }

    @Test
    void republishAll_syncsEveryInstance() {
        Instance a = spy(instanceWithPrefix("plant"));
        UUID aId = UUID.randomUUID();
        doReturn(aId).when(a).getId();
        when(instanceRepository.findAll()).thenReturn(List.of(a));
        when(instanceRepository.findById(aId)).thenReturn(Optional.of(a));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(aId)).thenReturn(List.of());

        serviceWithMqtt().onMqttAvailable();

        verify(mqttPublisher).publishRetained("plant/pump/request", "{\"id\":null}");
    }

    // --- recordFlowReceived ---

    @Test
    void recordFlowReceived_withId_setsLitersOnThatEvent() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent running = requestedEvent(id, eventId, OffsetDateTime.now().plusMinutes(5));
        running.setStartedAt(OffsetDateTime.now().minusSeconds(60));
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(running));

        serviceWithMqtt().recordFlowReceived(id, 0.35, "manual", eventId.toString());

        assertEquals(BigDecimal.valueOf(0.35), running.getLiters());
        assertNull(running.getStoppedAt());
    }

    @Test
    void recordFlowReceived_withoutId_usesNewestOpenEventOfTrigger() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "schedule");
        when(wateringEventRepository.findOpenByTrigger(id, "schedule")).thenReturn(List.of(open));

        serviceWithMqtt().recordFlowReceived(id, 0.35, "schedule", null);

        verify(wateringEventRepository).save(open);
        assertEquals(BigDecimal.valueOf(0.35), open.getLiters());
    }

    @Test
    void recordFlowReceived_noOpenEvent_doesNothing() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of());

        serviceWithMqtt().recordFlowReceived(id, 0.35, "manual", null);

        verify(wateringEventRepository, never()).save(any());
    }

    // --- recordPumpStatus ---

    private static final long TS = 1757764800L;

    @Test
    void recordPumpStatus_onWithId_startsRequestAndClearsRetained() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent request = requestedEvent(id, eventId, OffsetDateTime.now().plusMinutes(5));
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(request));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "on", "manual", null, TS, eventId.toString());

        assertEquals(TS, request.getStartedAt().toEpochSecond());
        verify(wateringEventRepository).save(request);
        verify(executor).execute(any(Runnable.class));   // never published on the caller's thread
        verify(mqttPublisher).publishRetained("plant/pump/request", "{\"id\":null}");
    }

    @Test
    void recordPumpStatus_duplicateOn_keepsFirstStartedAt() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent running = requestedEvent(id, eventId, OffsetDateTime.now().plusMinutes(5));
        OffsetDateTime first = OffsetDateTime.now().minusSeconds(40);
        running.setStartedAt(first);
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(running));

        serviceWithMqtt().recordPumpStatus(id, "on", "manual", null, TS, eventId.toString());

        assertEquals(first, running.getStartedAt());
    }

    @Test
    void recordPumpStatus_onForExpiredRequest_reopensIt() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent expired = requestedEvent(id, eventId, OffsetDateTime.now().minusMinutes(2));
        expired.setStoppedAt(OffsetDateTime.now().minusMinutes(1));
        expired.setOutcome("expired");
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(expired));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "on", "manual", null, TS, eventId.toString());

        assertEquals(TS, expired.getStartedAt().toEpochSecond());
        assertNull(expired.getStoppedAt());
        assertNull(expired.getOutcome());
    }

    @Test
    void recordPumpStatus_unknownId_isIgnored() {
        UUID id = UUID.randomUUID();
        UUID foreignEvent = UUID.randomUUID();
        WateringEvent other = requestedEvent(UUID.randomUUID(), foreignEvent, OffsetDateTime.now().plusMinutes(5));
        when(wateringEventRepository.findById(foreignEvent)).thenReturn(Optional.of(other));

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS, foreignEvent.toString());
        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS, "not-a-uuid");

        verify(wateringEventRepository, never()).save(any());
        verify(wateringEventRepository, never()).findOpenByTrigger(any(), any());
    }

    @Test
    void recordPumpStatus_rejectedWithId_closesRequestWithoutStartAndClearsRetained() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent request = requestedEvent(id, eventId, OffsetDateTime.now().plusMinutes(5));
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(request));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "rejected", "manual", "low_battery", TS, eventId.toString());

        assertEquals("low_battery", request.getOutcome());
        assertEquals(TS, request.getStoppedAt().toEpochSecond());
        assertNull(request.getStartedAt());
        verify(mqttPublisher).publishRetained("plant/pump/request", "{\"id\":null}");
    }

    @Test
    void recordPumpStatus_offWithId_closesRunWithOutcome() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WateringEvent running = requestedEvent(id, eventId, OffsetDateTime.now().plusMinutes(5));
        running.setStartedAt(OffsetDateTime.now().minusSeconds(120));
        when(wateringEventRepository.findById(eventId)).thenReturn(Optional.of(running));

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS, eventId.toString());

        assertEquals("completed", running.getOutcome());
        assertEquals(TS, running.getStoppedAt().toEpochSecond());
    }

    @Test
    void recordPumpStatus_legacyOnWithoutId_startsNewestOpenAppEvent() {
        UUID id = UUID.randomUUID();
        WateringEvent request = requestedEvent(id, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(5));
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of(request));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "on", "manual", null, TS, null);

        assertEquals(TS, request.getStartedAt().toEpochSecond());
        verify(mqttPublisher).publishRetained("plant/pump/request", "{\"id\":null}");
    }

    @Test
    void recordPumpStatus_offManual_closesOpenAppEventWithOutcome() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of(open));

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS, null);

        verify(wateringEventRepository).save(open);
        assertEquals("completed", open.getOutcome());
        assertEquals(TS, open.getStoppedAt().toEpochSecond());
    }

    @Test
    void recordPumpStatus_withoutTimestamp_usesReceiveTime() {
        UUID id = UUID.randomUUID();
        WateringEvent open = openEvent(id, "app");
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of(open));

        serviceWithMqtt().recordPumpStatus(id, "off", null, null, 0L, null);

        assertTrue(open.getStoppedAt().isAfter(OffsetDateTime.now().minusSeconds(5)));
        assertNull(open.getOutcome());
    }

    @Test
    void recordPumpStatus_onSchedule_createsRunningScheduleEvent() {
        UUID id = UUID.randomUUID();

        serviceWithMqtt().recordPumpStatus(id, "on", "schedule", null, TS, null);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals("schedule", saved.getTriggeredBy());
        assertEquals(TS, saved.getStartedAt().toEpochSecond());
        assertNull(saved.getStoppedAt());
        assertNull(saved.getRequestedAt());
        verifyNoInteractions(executor);   // schedule runs have no retained request
    }

    @Test
    void recordPumpStatus_missedScheduleWithoutOpenEvent_createsClosedEventThatNeverStarted() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findOpenByTrigger(id, "schedule")).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "rejected", "schedule", "missed", TS, null);

        ArgumentCaptor<WateringEvent> captor = ArgumentCaptor.forClass(WateringEvent.class);
        verify(wateringEventRepository).save(captor.capture());
        WateringEvent saved = captor.getValue();
        assertEquals("missed", saved.getOutcome());
        assertNull(saved.getStartedAt());          // did not run -> does not reset tankEmpty
        assertEquals(TS, saved.getStoppedAt().toEpochSecond());
    }

    @Test
    void recordPumpStatus_offManualWithoutOpenEvent_doesNothing() {
        UUID id = UUID.randomUUID();
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of());

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "command", TS, null);

        verify(wateringEventRepository, never()).save(any());
    }

    // --- safety net ---

    @Test
    void closeStale_expiresUnansweredRequestAfterGraceAndClearsRetained() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        WateringEvent stale = requestedEvent(id, UUID.randomUUID(), now.minusSeconds(61));
        UUID freshId = UUID.randomUUID();
        WateringEvent fresh = requestedEvent(id, freshId, now.minusSeconds(30)); // still in grace
        when(wateringEventRepository.findByStoppedAtIsNull()).thenReturn(List.of(stale, fresh));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of(fresh));

        serviceWithMqtt().closeStale(now);

        assertEquals("expired", stale.getOutcome());
        assertEquals(now, stale.getStoppedAt());
        assertNull(fresh.getOutcome());
        verify(mqttPublisher).publishRetained(eq("plant/pump/request"), contains(freshId.toString()));
    }

    @Test
    void closeStale_interruptsRunPastItsDurationPlusFiveMinutes() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        WateringEvent manual = requestedEvent(id, UUID.randomUUID(), now.plusMinutes(1));
        manual.setStartedAt(now.minusSeconds(120 + 301));       // 120 s run + 5 min grace exceeded
        WateringEvent schedule = openEvent(id, "schedule");
        schedule.setStartedAt(now.minusSeconds(600 + 299));     // 600 s default + grace not yet exceeded
        when(wateringEventRepository.findByStoppedAtIsNull()).thenReturn(List.of(manual, schedule));

        serviceWithMqtt().closeStale(now);

        assertEquals("interrupted", manual.getOutcome());
        assertNull(schedule.getOutcome());
        verify(mqttPublisher, never()).publishRetained(any(), any());
    }

    // --- final review fixes ---

    @Test
    void start_publishesRetainedRequestOnlyThroughTheExecutor() {
        // Syncs from HTTP and executor threads must not overtake each other: all go through the one executor thread
        UUID id = UUID.randomUUID();
        doNothing().when(executor).execute(any(Runnable.class));   // queue, don't run
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());

        serviceWithMqtt().start(id, 60);

        verify(executor).execute(any(Runnable.class));
        verify(mqttPublisher, never()).publishRetained(any(), any());
    }

    @Test
    void start_concurrentCalls_areSerialized() throws Exception {
        // A double tap must not leave two open requests (the older one would water again later)
        UUID id = UUID.randomUUID();
        doNothing().when(executor).execute(any(Runnable.class));
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instanceWithPrefix("plant")));
        when(wateringEventRepository.findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id)).thenReturn(List.of());
        CountDownLatch inSave = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger saves = new AtomicInteger();
        when(wateringEventRepository.save(any())).thenAnswer(inv -> {
            if (saves.incrementAndGet() == 1) {
                inSave.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return inv.getArgument(0);
        });
        PumpService service = serviceWithMqtt();

        Thread first = new Thread(() -> service.start(id, 60));
        first.start();
        assertTrue(inSave.await(5, TimeUnit.SECONDS));
        Thread second = new Thread(() -> service.start(id, 60));
        second.start();
        second.join(300);   // must still be waiting for the first start to finish

        verify(wateringEventRepository, times(1)).findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id);
        release.countDown();
        first.join(5000);
        second.join(5000);
        verify(wateringEventRepository, times(2)).findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(id);
    }

    @Test
    void closeStale_interruptedRunStopsAtItsPlannedEndNotAtDetectionTime() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        WateringEvent manual = requestedEvent(id, UUID.randomUUID(), now.plusMinutes(1));
        OffsetDateTime started = now.minusDays(3);              // e.g. left open by an older version
        manual.setStartedAt(started);
        when(wateringEventRepository.findByStoppedAtIsNull()).thenReturn(List.of(manual));

        serviceWithMqtt().closeStale(now);

        assertEquals("interrupted", manual.getOutcome());
        assertEquals(started.plusSeconds(120), manual.getStoppedAt());
    }

    @Test
    void recordPumpStatus_lateOffWithoutId_repairsRunTheSafetyNetInterrupted() {
        // WiFi outage longer than the run: the safety net closed it, then the queued "off" arrives
        UUID id = UUID.randomUUID();
        WateringEvent interrupted = openEvent(id, "schedule");
        OffsetDateTime started = OffsetDateTime.ofInstant(Instant.ofEpochSecond(TS - 300), ZoneOffset.UTC);
        interrupted.setStartedAt(started);
        interrupted.setStoppedAt(started.plusSeconds(600));
        interrupted.setOutcome("interrupted");
        when(wateringEventRepository.findOpenByTrigger(id, "schedule")).thenReturn(List.of());
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndOutcomeOrderByStartedAtDesc(
                id, "schedule", "interrupted")).thenReturn(Optional.of(interrupted));

        serviceWithMqtt().recordPumpStatus(id, "off", "schedule", "completed", TS, null);

        assertEquals("completed", interrupted.getOutcome());
        assertEquals(TS, interrupted.getStoppedAt().toEpochSecond());
        verify(wateringEventRepository).save(interrupted);   // no phantom event
        verify(wateringEventRepository, times(1)).save(any());
    }

    @Test
    void recordPumpStatus_offFarAfterAnInterruptedRun_doesNotRewriteIt() {
        UUID id = UUID.randomUUID();
        WateringEvent old = openEvent(id, "app");
        OffsetDateTime started = OffsetDateTime.ofInstant(Instant.ofEpochSecond(TS - 86_400), ZoneOffset.UTC);
        old.setStartedAt(started);
        old.setStoppedAt(started.plusSeconds(600));
        old.setOutcome("interrupted");
        when(wateringEventRepository.findOpenByTrigger(id, "app")).thenReturn(List.of());
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndOutcomeOrderByStartedAtDesc(
                id, "app", "interrupted")).thenReturn(Optional.of(old));

        serviceWithMqtt().recordPumpStatus(id, "off", "manual", "completed", TS, null);

        assertEquals("interrupted", old.getOutcome());
        verify(wateringEventRepository, never()).save(any());
    }

    @Test
    void recordFlowReceived_lateFlowWithoutId_fillsLitersOfInterruptedRun() {
        UUID id = UUID.randomUUID();
        WateringEvent interrupted = openEvent(id, "schedule");
        interrupted.setStoppedAt(OffsetDateTime.now());
        interrupted.setOutcome("interrupted");
        when(wateringEventRepository.findOpenByTrigger(id, "schedule")).thenReturn(List.of());
        when(wateringEventRepository.findFirstByInstanceIdAndTriggeredByAndOutcomeOrderByStartedAtDesc(
                id, "schedule", "interrupted")).thenReturn(Optional.of(interrupted));

        serviceWithMqtt().recordFlowReceived(id, 0.5, "schedule", null);

        assertEquals(BigDecimal.valueOf(0.5), interrupted.getLiters());
    }

    @Test
    void getHistory_openRequest_carriesItsExpiry() {
        // The app's pump card shows the open request (time, duration, expiry) from the history
        UUID id = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.now().plusMinutes(15);
        WateringEvent open = requestedEvent(id, UUID.randomUUID(), expires);
        when(wateringEventRepository.findHistory(id)).thenReturn(List.of(open));

        WateringEventResponse response = serviceWithMqtt().getHistory(id).get(0);

        assertEquals(expires, response.expiresAt());
        assertNull(response.startedAt());
        assertNull(response.stoppedAt());
    }

    // --- getHistory ---

    @Test
    void getHistory_returnsMappedResponsesWithDuration() {
        UUID id = UUID.randomUUID();
        WateringEvent event = new WateringEvent();
        event.setInstanceId(id);
        event.setRequestedAt(OffsetDateTime.now().minusSeconds(90));
        event.setRequestedDurationSeconds(60);
        event.setStartedAt(OffsetDateTime.now().minusSeconds(60));
        event.setStoppedAt(OffsetDateTime.now());
        event.setLiters(BigDecimal.valueOf(1.5));
        event.setTriggeredBy("app");
        event.setOutcome("completed");
        when(wateringEventRepository.findHistory(id)).thenReturn(List.of(event));

        WateringEventResponse response = serviceWithMqtt().getHistory(id).get(0);

        assertEquals(BigDecimal.valueOf(1.5), response.liters());
        assertTrue(response.durationSeconds() >= 59);
        assertEquals("completed", response.outcome());
        assertEquals(60, response.requestedDurationSeconds());
        assertNotNull(response.requestedAt());
    }

    @Test
    void getHistory_runningEvent_hasDurationNull() {
        UUID id = UUID.randomUUID();
        WateringEvent event = new WateringEvent();
        event.setInstanceId(id);
        event.setStartedAt(OffsetDateTime.now());
        when(wateringEventRepository.findHistory(id)).thenReturn(List.of(event));

        assertNull(serviceWithMqtt().getHistory(id).get(0).durationSeconds());
    }

    @Test
    void getHistory_requestThatNeverStarted_hasNoStartAndNoDuration() {
        UUID id = UUID.randomUUID();
        WateringEvent event = new WateringEvent();
        event.setInstanceId(id);
        event.setTriggeredBy("app");
        event.setRequestedAt(OffsetDateTime.now().minusMinutes(25));
        event.setStoppedAt(OffsetDateTime.now());
        event.setOutcome("expired");
        when(wateringEventRepository.findHistory(id)).thenReturn(List.of(event));

        WateringEventResponse response = serviceWithMqtt().getHistory(id).get(0);

        assertNull(response.startedAt());
        assertNull(response.durationSeconds());
        assertEquals("expired", response.outcome());
    }
}
