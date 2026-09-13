package com.plant_watering_system.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "moisture_reading")
public class MoistureReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "sensor_index", nullable = false)
    private short sensorIndex;

    @Column(nullable = false)
    private double percent;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    public Long getId() { return id; }
    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public short getSensorIndex() { return sensorIndex; }
    public void setSensorIndex(short sensorIndex) { this.sensorIndex = sensorIndex; }
    public double getPercent() { return percent; }
    public void setPercent(double percent) { this.percent = percent; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime measuredAt) { this.measuredAt = measuredAt; }
}
