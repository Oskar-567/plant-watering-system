package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.ScheduleRequest;
import com.plant_watering_system.server.dto.ScheduleResponse;
import com.plant_watering_system.server.service.ScheduleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/instances/{id}/schedule")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ScheduleResponse get(@PathVariable UUID id) {
        return scheduleService.get(id);
    }

    // Replaces the whole schedule and pushes it to the ESP32 (synced=false until it acks)
    @PutMapping
    public ScheduleResponse replace(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.replace(id, request);
    }
}
