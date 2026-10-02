package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.InstanceRequest;
import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock InstanceRepository instanceRepository;
    @Mock WateringEventRepository wateringEventRepository;

    private InstanceService service() {
        return new InstanceService(instanceRepository, wateringEventRepository);
    }

    // Instance has no setId (id is generated), so a mock provides the id
    private Instance instanceWithId(UUID id) {
        Instance instance = mock(Instance.class);
        when(instance.getId()).thenReturn(id);
        return instance;
    }

    @Test
    void findAll_derivesStatusFlagsWithOneQueryPerFlag() {
        UUID running = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        UUID tankEmpty = UUID.randomUUID();
        List<Instance> instances = List.of(instanceWithId(running), instanceWithId(requested), instanceWithId(tankEmpty));
        List<UUID> ids = List.of(running, requested, tankEmpty);
        when(instanceRepository.findAll()).thenReturn(instances);
        when(wateringEventRepository.findInstanceIdsWithRunningEvent(ids)).thenReturn(Set.of(running));
        when(wateringEventRepository.findInstanceIdsWithRequestedEvent(ids)).thenReturn(Set.of(requested));
        when(wateringEventRepository.findInstanceIdsWithTankEmpty(ids)).thenReturn(Set.of(tankEmpty));

        List<InstanceResponse> result = service().findAll();

        assertEquals(List.of(true, false, false), result.stream().map(InstanceResponse::pumpRunning).toList());
        assertEquals(List.of(false, true, false), result.stream().map(InstanceResponse::pumpRequested).toList());
        assertEquals(List.of(false, false, true), result.stream().map(InstanceResponse::tankEmpty).toList());
        verify(wateringEventRepository).findInstanceIdsWithRunningEvent(any());
        verify(wateringEventRepository).findInstanceIdsWithRequestedEvent(any());
        verify(wateringEventRepository).findInstanceIdsWithTankEmpty(any());
        verifyNoMoreInteractions(wateringEventRepository);
    }

    @Test
    void findAll_withoutInstances_skipsStatusQueries() {
        when(instanceRepository.findAll()).thenReturn(List.of());

        assertEquals(List.of(), service().findAll());
        verifyNoInteractions(wateringEventRepository);
    }

    @Test
    void findById_includesStatusFlagsAndLastSeen() {
        UUID id = UUID.randomUUID();
        OffsetDateTime seen = OffsetDateTime.parse("2026-10-02T12:00:00Z");
        Instance instance = instanceWithId(id);
        when(instance.getLastSeenAt()).thenReturn(seen);
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instance));
        when(wateringEventRepository.findInstanceIdsWithRunningEvent(List.of(id))).thenReturn(Set.of(id));
        when(wateringEventRepository.findInstanceIdsWithRequestedEvent(List.of(id))).thenReturn(Set.of());
        when(wateringEventRepository.findInstanceIdsWithTankEmpty(List.of(id))).thenReturn(Set.of(id));

        InstanceResponse result = service().findById(id);

        assertTrue(result.pumpRunning());
        assertFalse(result.pumpRequested());
        assertTrue(result.tankEmpty());
        assertEquals(seen, result.lastSeenAt());
    }

    @Test
    void create_newInstanceIsIdleWithoutQueryingEvents() {
        UUID id = UUID.randomUUID();
        Instance saved = instanceWithId(id);
        when(instanceRepository.save(any())).thenReturn(saved);

        InstanceResponse result = service().create(
                new InstanceRequest("Balkon", "plant", true, true, 1, null, null));

        assertFalse(result.pumpRunning());
        assertFalse(result.pumpRequested());
        assertFalse(result.tankEmpty());
        verifyNoInteractions(wateringEventRepository);
    }
}
