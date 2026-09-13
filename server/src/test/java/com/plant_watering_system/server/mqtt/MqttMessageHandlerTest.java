package com.plant_watering_system.server.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.service.PumpService;
import com.plant_watering_system.server.service.SensorReadingService;
import com.plant_watering_system.server.service.TankEmptyDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttMessageHandlerTest {

    @Mock InstanceRepository instanceRepository;
    @Mock SensorReadingService sensorReadingService;
    @Mock PumpService pumpService;
    @Mock TankEmptyDetectionService tankEmptyDetectionService;

    private final UUID instanceId = UUID.randomUUID();

    private MqttMessageHandler handler() {
        return new MqttMessageHandler(
                instanceRepository, sensorReadingService, pumpService, tankEmptyDetectionService, new ObjectMapper());
    }

    // Instance has no setId (id is generated), so a mock provides the id
    private void givenInstanceWithPrefix(String prefix) {
        Instance instance = mock(Instance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instanceRepository.findByMqttPrefix(prefix)).thenReturn(Optional.of(instance));
    }

    @Test
    void moisturePayload_recordsOneReadingPerSensor() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/moisture", "{\"sensor_0\":42,\"sensor_1\":67,\"sensor_2\":55}");

        verify(sensorReadingService).recordMoisture(instanceId, 0, 42.0);
        verify(sensorReadingService).recordMoisture(instanceId, 1, 67.0);
        verify(sensorReadingService).recordMoisture(instanceId, 2, 55.0);
        verifyNoMoreInteractions(sensorReadingService);
    }

    @Test
    void batteryPayload_recordsBatteryReading() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/battery", "{\"soc\":78.1,\"voltage\":3.91}");

        verify(sensorReadingService).recordBattery(instanceId, 78.1, 3.91);
        verifyNoMoreInteractions(sensorReadingService);
    }

    @Test
    void flowPayload_notifiesPumpAndTankDetectionWithoutStoringReading() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/flow", "{\"liters\":0.35}");

        verify(pumpService).recordFlowReceived(instanceId, 0.35);
        verify(tankEmptyDetectionService).onFlowReceived(instanceId, 0.35);
        verifyNoInteractions(sensorReadingService);
    }

    @Test
    void unknownPrefix_recordsNothing() {
        when(instanceRepository.findByMqttPrefix("unknown")).thenReturn(Optional.empty());

        handler().handle("unknown/sensors/moisture", "{\"sensor_0\":42}");

        verifyNoInteractions(sensorReadingService, pumpService, tankEmptyDetectionService);
    }
}
