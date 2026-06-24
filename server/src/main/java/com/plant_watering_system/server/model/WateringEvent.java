package com.plant_watering_system.server.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watering_event")
public class WateringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "stopped_at")
    private OffsetDateTime stoppedAt;

    private BigDecimal liters;

    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;

    public UUID getId() { return id; }
    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(OffsetDateTime stoppedAt) { this.stoppedAt = stoppedAt; }
    public BigDecimal getLiters() { return liters; }
    public void setLiters(BigDecimal liters) { this.liters = liters; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
}
