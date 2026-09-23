package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.WateringSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface WateringScheduleRepository extends JpaRepository<WateringSchedule, UUID> {

    // Row lock for read-modify-write of the version (caller must be @Transactional)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WateringSchedule s where s.instanceId = :instanceId")
    Optional<WateringSchedule> findByIdForUpdate(@Param("instanceId") UUID instanceId);

    // Single-column update: never writes a stale copy of the entries back
    @Transactional
    @Modifying
    @Query("update WateringSchedule s set s.acknowledgedVersion = :version where s.instanceId = :instanceId")
    int updateAcknowledgedVersion(@Param("instanceId") UUID instanceId, @Param("version") int version);
}
