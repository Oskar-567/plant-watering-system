package com.plant_watering_system.server.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String password) {}
