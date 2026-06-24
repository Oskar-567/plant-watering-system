package com.plant_watering_system.server.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WateringEventResponse(
        UUID id,
        UUID instanceId,
        OffsetDateTime startedAt,
        OffsetDateTime stoppedAt,
        BigDecimal liters,
        String triggeredBy,
        Long durationSeconds
) {}
