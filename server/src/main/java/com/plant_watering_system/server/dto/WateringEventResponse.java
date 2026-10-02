package com.plant_watering_system.server.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WateringEventResponse(
        UUID id,
        UUID instanceId,
        OffsetDateTime requestedAt,
        Integer requestedDurationSeconds,
        OffsetDateTime expiresAt,       // requests only: the device must pick it up before this
        OffsetDateTime startedAt,       // null = the pump never ran (request expired/cancelled/refused)
        OffsetDateTime stoppedAt,
        BigDecimal liters,
        String triggeredBy,
        String outcome,
        Long durationSeconds
) {}
