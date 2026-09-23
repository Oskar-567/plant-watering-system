package com.plant_watering_system.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "watering_schedule")
public class WateringSchedule {

    @Id
    @Column(name = "instance_id")
    private UUID instanceId;

    @Column(nullable = false)
    private int version;

    @Column(name = "acknowledged_version")
    private Integer acknowledgedVersion;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "watering_schedule_entry", joinColumns = @JoinColumn(name = "instance_id"))
    @OrderColumn(name = "position")
    private List<ScheduleEntry> entries = new ArrayList<>();

    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Integer getAcknowledgedVersion() { return acknowledgedVersion; }
    public void setAcknowledgedVersion(Integer acknowledgedVersion) { this.acknowledgedVersion = acknowledgedVersion; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ScheduleEntry> getEntries() { return entries; }
}
