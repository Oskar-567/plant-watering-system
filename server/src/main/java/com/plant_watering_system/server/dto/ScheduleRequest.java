package com.plant_watering_system.server.dto;

import com.plant_watering_system.server.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Replaces the whole schedule; an empty list disables schedule watering
public record ScheduleRequest(
        @NotNull @Size(max = ScheduleService.MAX_ENTRIES) List<@NotNull @Valid ScheduleEntryRequest> entries
) {}
