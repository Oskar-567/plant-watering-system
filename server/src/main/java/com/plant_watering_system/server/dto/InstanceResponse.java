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
        @Schema(description = "A run has started on the device and not ended yet")
        boolean pumpRunning,
        @Schema(description = "A manual request waits for the device's next wake")
        boolean pumpRequested,
        @Schema(description = "The latest run that actually started ended with outcome flow_stall")
        boolean tankEmpty,
        @Schema(description = "Receive time of the latest MQTT message from the device, null = never seen")
        OffsetDateTime lastSeenAt
) {
}
