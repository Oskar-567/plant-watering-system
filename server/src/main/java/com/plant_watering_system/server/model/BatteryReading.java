package com.plant_watering_system.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "battery_reading")
public class BatteryReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(nullable = false)
    private double soc;

    @Column(nullable = false)
    private double voltage;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    public Long getId() { return id; }
    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public double getSoc() { return soc; }
    public void setSoc(double soc) { this.soc = soc; }
    public double getVoltage() { return voltage; }
    public void setVoltage(double voltage) { this.voltage = voltage; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime measuredAt) { this.measuredAt = measuredAt; }
}
