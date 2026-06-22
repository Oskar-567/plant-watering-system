package com.plant_watering_system.server.dto;

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
        OffsetDateTime createdAt
) {
}
