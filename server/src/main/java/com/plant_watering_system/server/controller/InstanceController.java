package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.*;
import com.plant_watering_system.server.service.SensorReadingService;
import com.plant_watering_system.server.service.InstanceService;
import com.plant_watering_system.server.service.PumpService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/instances")
@SecurityRequirement(name = "bearerAuth")
public class InstanceController {

    private static final String RANGE_PATTERN = "[1-9][0-9]{0,2}[mhd]";

    private final InstanceService service;
    private final PumpService pumpService;
    private final SensorReadingService sensorReadingService;

    public InstanceController(InstanceService service, PumpService pumpService, SensorReadingService sensorReadingService) {
        this.service = service;
        this.pumpService = pumpService;
        this.sensorReadingService = sensorReadingService;
    }

    @GetMapping
    public List<InstanceResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public InstanceResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstanceResponse create(@Valid @RequestBody InstanceRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/pump/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pumpStart(@PathVariable UUID id) {
        pumpService.start(id);
    }

    @PostMapping("/{id}/pump/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pumpStop(@PathVariable UUID id) {
        pumpService.stop(id);
    }

    @GetMapping("/{id}/watering-history")
    public List<WateringEventResponse> wateringHistory(@PathVariable UUID id) {
        return pumpService.getHistory(id);
    }

    @GetMapping("/{id}/moisture")
    public List<MoisturePoint> getMoisture(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "24h") @Pattern(regexp = RANGE_PATTERN) String range) {
        return sensorReadingService.getMoisture(id, parseRange(range));
    }

    @GetMapping("/{id}/battery")
    public List<BatteryPoint> getBattery(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "24h") @Pattern(regexp = RANGE_PATTERN) String range) {
        return sensorReadingService.getBattery(id, parseRange(range));
    }

    // Input is already validated against RANGE_PATTERN, e.g. "30m", "24h", "7d"
    private static Duration parseRange(String range) {
        long amount = Long.parseLong(range.substring(0, range.length() - 1));
        return switch (range.charAt(range.length() - 1)) {
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }
}
