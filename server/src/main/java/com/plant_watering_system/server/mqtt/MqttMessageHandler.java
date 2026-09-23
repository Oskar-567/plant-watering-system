package com.plant_watering_system.server.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.service.PumpService;
import com.plant_watering_system.server.service.ScheduleService;
import com.plant_watering_system.server.service.SensorReadingService;
import com.plant_watering_system.server.service.TankEmptyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final InstanceRepository instanceRepository;
    private final SensorReadingService sensorReadingService;
    private final PumpService pumpService;
    private final TankEmptyDetectionService tankEmptyDetectionService;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(
            InstanceRepository instanceRepository,
            SensorReadingService sensorReadingService,
            PumpService pumpService,
            TankEmptyDetectionService tankEmptyDetectionService,
            ScheduleService scheduleService,
            ObjectMapper objectMapper) {
        this.instanceRepository = instanceRepository;
        this.sensorReadingService = sensorReadingService;
        this.pumpService = pumpService;
        this.tankEmptyDetectionService = tankEmptyDetectionService;
        this.scheduleService = scheduleService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String payload) {
        String[] parts = topic.split("/", 2);
        if (parts.length < 2) {
            log.warn("Unexpected topic format: {}", topic);
            return;
        }
        String prefix = parts[0];
        String suffix = parts[1];

        Optional<Instance> instance = instanceRepository.findByMqttPrefix(prefix);
        if (instance.isEmpty()) {
            log.warn("No instance found for mqtt prefix: {}", prefix);
            return;
        }
        UUID instanceId = instance.get().getId();

        switch (suffix) {
            case "sensors/moisture" -> handleMoisture(instanceId, payload);
            case "sensors/flow"     -> handleFlow(instanceId, payload);
            case "sensors/battery"  -> handleBattery(instanceId, payload);
            case "status"           -> handleStatus(instanceId, payload);
            case "schedule/ack"     -> handleScheduleAck(instanceId, payload);
            default -> log.warn("Unknown topic suffix: {}", suffix);
        }
    }

    private void handleMoisture(UUID instanceId, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getKey().startsWith("sensor_")) {
                    int index = Integer.parseInt(entry.getKey().substring(7));
                    double percent = ((Number) entry.getValue()).doubleValue();
                    sensorReadingService.recordMoisture(instanceId, index, percent);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse moisture payload: {}", payload, e);
        }
    }

    private void handleFlow(UUID instanceId, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            double liters = ((Number) data.get("liters")).doubleValue();
            pumpService.recordFlowReceived(instanceId, liters, (String) data.get("trigger"));
            tankEmptyDetectionService.onFlowReceived(instanceId, liters);
        } catch (Exception e) {
            log.warn("Failed to parse flow payload: {}", payload, e);
        }
    }

    private void handleBattery(UUID instanceId, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            double soc = ((Number) data.get("soc")).doubleValue();
            double voltage = ((Number) data.get("voltage")).doubleValue();
            sensorReadingService.recordBattery(instanceId, soc, voltage);
        } catch (Exception e) {
            log.warn("Failed to parse battery payload: {}", payload, e);
        }
    }

    private void handleStatus(UUID instanceId, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            if (!data.containsKey("pump")) return;

            String pumpStatus = (String) data.get("pump");
            // "rejected" = the pump never ran, nothing for tank detection to track
            if ("on".equals(pumpStatus) || "off".equals(pumpStatus)) {
                tankEmptyDetectionService.onPumpStatus(instanceId, pumpStatus);
            }
            long ts = data.get("ts") instanceof Number n ? n.longValue() : 0L;
            pumpService.recordPumpStatus(
                    instanceId, pumpStatus, (String) data.get("trigger"), (String) data.get("reason"), ts);
        } catch (Exception e) {
            log.warn("Failed to parse status payload: {}", payload, e);
        }
    }

    private void handleScheduleAck(UUID instanceId, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            int version = ((Number) data.get("version")).intValue();
            scheduleService.recordAck(instanceId, version);
        } catch (Exception e) {
            log.warn("Failed to parse schedule ack payload: {}", payload, e);
        }
    }
}
