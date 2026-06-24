package com.plant_watering_system.server.dto;

import java.time.Instant;

public record MoisturePoint(Instant time, int sensorIndex, double percent) {}
