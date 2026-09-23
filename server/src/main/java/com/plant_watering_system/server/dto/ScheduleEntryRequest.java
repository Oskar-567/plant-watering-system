package com.plant_watering_system.server.dto;

import com.plant_watering_system.server.service.PumpService;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.util.Set;

public record ScheduleEntryRequest(
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String time,
        @NotNull @Min(1) @Max(PumpService.MAX_DURATION_SECONDS) Integer durationSeconds,
        @NotEmpty Set<DayOfWeek> days
) {}
