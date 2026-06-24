package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.*;
import com.plant_watering_system.server.influx.InfluxQueryService;
import com.plant_watering_system.server.service.InstanceService;
import com.plant_watering_system.server.service.PumpService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/instances")
@SecurityRequirement(name = "bearerAuth")
public class InstanceController {

    private final InstanceService service;
    private final PumpService pumpService;
    private final InfluxQueryService influxQueryService;

    public InstanceController(InstanceService service, PumpService pumpService, InfluxQueryService influxQueryService) {
        this.service = service;
        this.pumpService = pumpService;
        this.influxQueryService = influxQueryService;
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
            @RequestParam(defaultValue = "24h") String range) {
        return influxQueryService.getMoisture(id.toString(), range);
    }

    @GetMapping("/{id}/battery")
    public List<BatteryPoint> getBattery(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "24h") String range) {
        return influxQueryService.getBattery(id.toString(), range);
    }
}
