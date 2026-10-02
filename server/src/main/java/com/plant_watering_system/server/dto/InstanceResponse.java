package com.plant_watering_system.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InstanceResponse(
        UUID id,
        String name,
        String mqttPrefix,
        boolean hasPump,
        boolean hasBattery,
        int sensorCount,
        BigDecimal latitude,
        BigDecimal longitude,
        OffsetDateTime createdAt,
        @Schema(description = "A watering event is still open (pump started, no off/rejected yet)")
        boolean pumpRunning,
        @Schema(description = "The most recent closed watering event ended with outcome flow_stall")
        boolean tankEmpty
) {
}
