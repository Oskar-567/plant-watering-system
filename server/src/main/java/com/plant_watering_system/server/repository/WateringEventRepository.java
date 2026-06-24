package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.WateringEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WateringEventRepository extends JpaRepository<WateringEvent, UUID> {

    List<WateringEvent> findByInstanceIdOrderByStartedAtDesc(UUID instanceId);

    Optional<WateringEvent> findByInstanceIdAndStoppedAtIsNull(UUID instanceId);
}
