package com.plant_watering_system.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class TankEmptyDetectionServiceTest {

    @Mock ScheduledExecutorService scheduler;
    @SuppressWarnings("rawtypes")
    @Mock ScheduledFuture future;

    private TankEmptyDetectionService service() {
        return new TankEmptyDetectionService(scheduler, 30);
    }

    @Test
    void flowReceived_cancelsExistingTimer() {
        UUID id = UUID.randomUUID();
        doReturn(future).when(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));

        TankEmptyDetectionService svc = service();
        svc.onPumpStatus(id, "on");
        svc.onFlowReceived(id, 0.35);

        verify(future).cancel(false);
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void timerExpiry_logsWarning() {
        UUID id = UUID.randomUUID();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(captor.capture(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(future);

        service().onPumpStatus(id, "on");
        captor.getValue().run();

        // no exception thrown — warning is logged, nothing else happens
    }
}
