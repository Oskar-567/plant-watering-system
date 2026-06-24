package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.InstanceRequest;
import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.dto.WateringEventResponse;
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

    public InstanceController(InstanceService service, PumpService pumpService) {
        this.service = service;
        this.pumpService = pumpService;
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
}
