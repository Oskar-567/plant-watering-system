package com.plant_watering_system.server.repository;

import com.plant_watering_system.server.model.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface InstanceRepository extends JpaRepository<Instance, UUID> {

    Optional<Instance> findByMqttPrefix(String mqttPrefix);

    // Monotonic: a late, out-of-order message never moves last_seen_at backwards
    @Transactional
    @Modifying
    @Query("update Instance i set i.lastSeenAt = :seenAt "
            + "where i.id = :id and (i.lastSeenAt is null or i.lastSeenAt < :seenAt)")
    int updateLastSeen(@Param("id") UUID id, @Param("seenAt") OffsetDateTime seenAt);
}
