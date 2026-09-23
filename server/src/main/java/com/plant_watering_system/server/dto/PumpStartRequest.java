package com.plant_watering_system.server.dto;

import com.plant_watering_system.server.service.PumpService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PumpStartRequest(
        @NotNull @Min(1) @Max(PumpService.MAX_DURATION_SECONDS) Integer durationSeconds
) {}
