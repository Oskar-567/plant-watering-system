package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.InstanceRequest;
import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.repository.WateringEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InstanceService {

    private final InstanceRepository repository;
    private final WateringEventRepository wateringEventRepository;

    public InstanceService(InstanceRepository repository, WateringEventRepository wateringEventRepository) {
        this.repository = repository;
        this.wateringEventRepository = wateringEventRepository;
    }

    public List<InstanceResponse> findAll() {
        return toResponses(repository.findAll());
    }

    public InstanceResponse findById(UUID id) {
        Instance instance = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponses(List.of(instance)).getFirst();
    }

    public InstanceResponse create(InstanceRequest request) {
        var instance = new Instance();
        instance.setName(request.name());
        instance.setMqttPrefix(request.mqttPrefix());
        instance.setHasPump(request.hasPump());
        instance.setHasBattery(request.hasBattery());
        instance.setSensorCount(request.sensorCount());
        instance.setLatitude(request.latitude());
        instance.setLongitude(request.longitude());
        // A new instance has no watering events yet
        return toResponse(repository.save(instance), false, false);
    }

    // Status flags come from two batch queries over all given instances, not one query per instance
    private List<InstanceResponse> toResponses(List<Instance> instances) {
        if (instances.isEmpty()) return List.of();
        List<UUID> ids = instances.stream().map(Instance::getId).toList();
        Set<UUID> running = wateringEventRepository.findInstanceIdsWithOpenEvent(ids);
        Set<UUID> tankEmpty = wateringEventRepository.findInstanceIdsWithTankEmpty(ids);
        return instances.stream()
                .map(i -> toResponse(i, running.contains(i.getId()), tankEmpty.contains(i.getId())))
                .toList();
    }

    private InstanceResponse toResponse(Instance i, boolean pumpRunning, boolean tankEmpty) {
        return new InstanceResponse(
                i.getId(), i.getName(), i.getMqttPrefix(),
                i.isHasPump(), i.isHasBattery(), i.getSensorCount(),
                i.getLatitude(), i.getLongitude(), i.getCreatedAt(),
                pumpRunning, tankEmpty
        );
    }
}
