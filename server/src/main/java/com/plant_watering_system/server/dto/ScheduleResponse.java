package com.plant_watering_system.server.dto;

import java.time.OffsetDateTime;
import java.util.List;

// synced = the ESP32 has acknowledged this version
public record ScheduleResponse(
        int version,
        Integer acknowledgedVersion,
        boolean synced,
        OffsetDateTime updatedAt,
        List<ScheduleEntryResponse> entries
) {}
