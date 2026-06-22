package com.plant_watering_system.server.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record InstanceRequest(
        @NotBlank String name,
        @NotBlank String mqttPrefix,
        boolean hasPump,
        boolean hasBattery,
        @Min(1) int sensorCount,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
