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
        UUID tankEmpty = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        List<Instance> instances = List.of(instanceWithId(running), instanceWithId(tankEmpty), instanceWithId(idle));
        when(instanceRepository.findAll()).thenReturn(instances);
        when(wateringEventRepository.findInstanceIdsWithOpenEvent(List.of(running, tankEmpty, idle)))
                .thenReturn(Set.of(running));
        when(wateringEventRepository.findInstanceIdsWithTankEmpty(List.of(running, tankEmpty, idle)))
                .thenReturn(Set.of(tankEmpty));

        List<InstanceResponse> result = service().findAll();

        assertEquals(List.of(true, false, false), result.stream().map(InstanceResponse::pumpRunning).toList());
        assertEquals(List.of(false, true, false), result.stream().map(InstanceResponse::tankEmpty).toList());
        verify(wateringEventRepository).findInstanceIdsWithOpenEvent(any());
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
    void findById_includesStatusFlags() {
        UUID id = UUID.randomUUID();
        Instance instance = instanceWithId(id);
        when(instanceRepository.findById(id)).thenReturn(Optional.of(instance));
        when(wateringEventRepository.findInstanceIdsWithOpenEvent(List.of(id))).thenReturn(Set.of(id));
        when(wateringEventRepository.findInstanceIdsWithTankEmpty(List.of(id))).thenReturn(Set.of(id));

        InstanceResponse result = service().findById(id);

        assertTrue(result.pumpRunning());
        assertTrue(result.tankEmpty());
    }

    @Test
    void create_newInstanceIsIdleWithoutQueryingEvents() {
        UUID id = UUID.randomUUID();
        Instance saved = instanceWithId(id);
        when(instanceRepository.save(any())).thenReturn(saved);

        InstanceResponse result = service().create(
                new InstanceRequest("Balkon", "plant", true, true, 1, null, null));

        assertFalse(result.pumpRunning());
        assertFalse(result.tankEmpty());
        verifyNoInteractions(wateringEventRepository);
    }
}
