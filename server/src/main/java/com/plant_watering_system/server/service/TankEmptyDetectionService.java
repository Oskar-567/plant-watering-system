package com.plant_watering_system.server.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TankEmptyDetectionService {

    public void onFlowReceived(UUID instanceId, double liters) {
        // implemented in #24
    }

    public void onPumpStatus(UUID instanceId, String pumpStatus) {
        // implemented in #24
    }
}
