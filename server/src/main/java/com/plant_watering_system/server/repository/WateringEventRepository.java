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

    // Includes an open request (startedAt and stoppedAt null): the app's pump card shows it,
    // the history list hides it. Never-started events sort by when they were requested or closed.
    @Query("select e from WateringEvent e where e.instanceId = :instanceId "
            + "order by coalesce(e.startedAt, e.requestedAt, e.stoppedAt) desc")
    List<WateringEvent> findHistory(@Param("instanceId") UUID instanceId);

    // Newest run of a trigger the safety net closed -- a late "off"/flow without id belongs to it
    Optional<WateringEvent> findFirstByInstanceIdAndTriggeredByAndOutcomeOrderByStartedAtDesc(
            UUID instanceId, String triggeredBy, String outcome);

    // Open events of all instances, for the safety net
    List<WateringEvent> findByStoppedAtIsNull();

    // Open requests: created by POST /pump/start, not yet answered by the device
    List<WateringEvent> findByInstanceIdAndStartedAtIsNullAndStoppedAtIsNull(UUID instanceId);

    // Open (requested or running) events of one trigger, newest first
    @Query("select e from WateringEvent e where e.instanceId = :instanceId "
            + "and e.triggeredBy = :triggeredBy and e.stoppedAt is null "
            + "order by coalesce(e.startedAt, e.requestedAt) desc")
    List<WateringEvent> findOpenByTrigger(@Param("instanceId") UUID instanceId,
                                          @Param("triggeredBy") String triggeredBy);

    @Query("select distinct e.instanceId from WateringEvent e "
            + "where e.instanceId in :instanceIds and e.startedAt is not null and e.stoppedAt is null")
    Set<UUID> findInstanceIdsWithRunningEvent(@Param("instanceIds") Collection<UUID> instanceIds);

    @Query("select distinct e.instanceId from WateringEvent e "
            + "where e.instanceId in :instanceIds and e.startedAt is null and e.stoppedAt is null")
    Set<UUID> findInstanceIdsWithRequestedEvent(@Param("instanceIds") Collection<UUID> instanceIds);

    // Latest closed run that actually started ended with flow_stall. Requests that
    // never ran (expired, cancelled, refused) have no started_at and are skipped.
    @Query("select distinct e.instanceId from WateringEvent e "
            + "where e.instanceId in :instanceIds and e.outcome = 'flow_stall' and e.startedAt is not null "
            + "and e.stoppedAt = (select max(e2.stoppedAt) from WateringEvent e2 "
            + "where e2.instanceId = e.instanceId and e2.startedAt is not null)")
    Set<UUID> findInstanceIdsWithTankEmpty(@Param("instanceIds") Collection<UUID> instanceIds);
}
