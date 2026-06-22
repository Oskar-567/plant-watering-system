package com.plant_watering_system.server.service;

import com.plant_watering_system.server.dto.InstanceRequest;
import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class InstanceService {

    private final InstanceRepository repository;

    public InstanceService(InstanceRepository repository) {
        this.repository = repository;
    }

    public List<InstanceResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public InstanceResponse findById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
        return toResponse(repository.save(instance));
    }

    private InstanceResponse toResponse(Instance i) {
        return new InstanceResponse(
                i.getId(), i.getName(), i.getMqttPrefix(),
                i.isHasPump(), i.isHasBattery(), i.getSensorCount(),
                i.getLatitude(), i.getLongitude(), i.getCreatedAt()
        );
    }
}
