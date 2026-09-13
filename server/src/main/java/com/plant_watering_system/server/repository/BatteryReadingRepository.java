package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.BatteryReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BatteryReadingRepository extends JpaRepository<BatteryReading, Long> {

    List<BatteryReading> findByInstanceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(UUID instanceId, OffsetDateTime since);
}
