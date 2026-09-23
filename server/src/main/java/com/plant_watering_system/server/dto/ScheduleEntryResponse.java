package com.plant_watering_system.server.dto;

import java.time.DayOfWeek;
import java.util.List;

public record ScheduleEntryResponse(String time, int durationSeconds, List<DayOfWeek> days) {}
