package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.WateringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WateringEventRepository extends JpaRepository<WateringEvent, UUID> {

    List<WateringEvent> findByInstanceIdOrderByStartedAtDesc(UUID instanceId);

    Optional<WateringEvent> findFirstByInstanceIdAndTriggeredByAndStoppedAtIsNullOrderByStartedAtDesc(
            UUID instanceId, String triggeredBy);

    @Query("select distinct e.instanceId from WateringEvent e "
            + "where e.instanceId in :instanceIds and e.stoppedAt is null")
    Set<UUID> findInstanceIdsWithOpenEvent(@Param("instanceIds") Collection<UUID> instanceIds);

    // Latest closed event per instance ended with flow_stall; max() ignores open events (stoppedAt null)
    @Query("select distinct e.instanceId from WateringEvent e "
            + "where e.instanceId in :instanceIds and e.outcome = 'flow_stall' "
            + "and e.stoppedAt = (select max(e2.stoppedAt) from WateringEvent e2 where e2.instanceId = e.instanceId)")
    Set<UUID> findInstanceIdsWithTankEmpty(@Param("instanceIds") Collection<UUID> instanceIds);
}
