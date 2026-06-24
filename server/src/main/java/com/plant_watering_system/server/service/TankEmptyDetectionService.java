package com.plant_watering_system.server.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class TankEmptyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(TankEmptyDetectionService.class);

    private final ScheduledExecutorService scheduler;
    private final long timeoutSeconds;
    private final Map<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public TankEmptyDetectionService(
            ScheduledExecutorService scheduler,
            @Value("${tank.empty.timeout-seconds:30}") long timeoutSeconds) {
        this.scheduler = scheduler;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void onPumpStatus(UUID instanceId, String pumpStatus) {
        if ("on".equals(pumpStatus)) {
            scheduleTimer(instanceId);
        } else {
            cancelTimer(instanceId);
        }
    }

    public void onFlowReceived(UUID instanceId, double liters) {
        scheduleTimer(instanceId);
    }

    private void scheduleTimer(UUID instanceId) {
        cancelTimer(instanceId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> onTankEmpty(instanceId),
                timeoutSeconds, TimeUnit.SECONDS
        );
        timers.put(instanceId, future);
    }

    private void cancelTimer(UUID instanceId) {
        ScheduledFuture<?> existing = timers.remove(instanceId);
        if (existing != null) existing.cancel(false);
    }

    private void onTankEmpty(UUID instanceId) {
        timers.remove(instanceId);
        log.warn("Tank empty detected for instance {}", instanceId);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
