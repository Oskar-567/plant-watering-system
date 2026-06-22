package com.plant_watering_system.server.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "instance")
public class Instance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "mqtt_prefix", nullable = false)
    private String mqttPrefix;

    @Column(name = "has_pump", nullable = false)
    private boolean hasPump = false;

    @Column(name = "has_battery", nullable = false)
    private boolean hasBattery = false;

    @Column(name = "sensor_count", nullable = false)
    private int sensorCount = 1;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMqttPrefix() { return mqttPrefix; }
    public void setMqttPrefix(String mqttPrefix) { this.mqttPrefix = mqttPrefix; }
    public boolean isHasPump() { return hasPump; }
    public void setHasPump(boolean hasPump) { this.hasPump = hasPump; }
    public boolean isHasBattery() { return hasBattery; }
    public void setHasBattery(boolean hasBattery) { this.hasBattery = hasBattery; }
    public int getSensorCount() { return sensorCount; }
    public void setSensorCount(int sensorCount) { this.sensorCount = sensorCount; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
