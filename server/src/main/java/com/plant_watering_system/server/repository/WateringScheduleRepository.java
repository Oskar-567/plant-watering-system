package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.WateringSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WateringScheduleRepository extends JpaRepository<WateringSchedule, UUID> {
}
