package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.Instance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstanceRepository extends JpaRepository<Instance, UUID> {

    Optional<Instance> findByMqttPrefix(String mqttPrefix);
}
