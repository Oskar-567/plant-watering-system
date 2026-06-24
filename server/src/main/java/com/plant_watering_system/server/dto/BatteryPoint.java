package com.plant_watering_system.server.dto;

import java.time.Instant;

public record BatteryPoint(Instant time, double soc, double voltage) {}
