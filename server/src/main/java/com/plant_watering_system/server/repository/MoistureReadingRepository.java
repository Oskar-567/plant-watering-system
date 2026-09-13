package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.MoistureReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MoistureReadingRepository extends JpaRepository<MoistureReading, Long> {

    List<MoistureReading> findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(UUID instanceId, OffsetDateTime since);
}
