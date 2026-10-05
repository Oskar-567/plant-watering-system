package com.plant_watering_system.server.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plant_watering_system.server.model.Instance;
import com.plant_watering_system.server.repository.InstanceRepository;
import com.plant_watering_system.server.service.PumpService;
import com.plant_watering_system.server.service.ScheduleService;
import com.plant_watering_system.server.service.SensorReadingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttMessageHandlerTest {

    @Mock InstanceRepository instanceRepository;
    @Mock SensorReadingService sensorReadingService;
    @Mock PumpService pumpService;
    @Mock ScheduleService scheduleService;

    private final UUID instanceId = UUID.randomUUID();

    private MqttMessageHandler handler() {
        return new MqttMessageHandler(
                instanceRepository, sensorReadingService, pumpService, scheduleService, new ObjectMapper());
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

        verify(sensorReadingService).recordMoisture(instanceId, 0, 42.0, 0L);
        verify(sensorReadingService).recordMoisture(instanceId, 1, 67.0, 0L);
        verify(sensorReadingService).recordMoisture(instanceId, 2, 55.0, 0L);
        verifyNoMoreInteractions(sensorReadingService);
    }

    @Test
    void batteryPayload_recordsBatteryReading() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/battery", "{\"soc\":78.1,\"voltage\":3.91}");

        verify(sensorReadingService).recordBattery(instanceId, 78.1, 3.91, 0L);
        verifyNoMoreInteractions(sensorReadingService);
    }

    @Test
    void flowPayload_notifiesPumpWithoutStoringReading() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/flow", "{\"liters\":0.35}");

        verify(pumpService).recordFlowReceived(instanceId, 0.35, null, null);
        verifyNoInteractions(sensorReadingService);
    }

    @Test
    void flowPayloadWithTrigger_passesTrigger() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/flow", "{\"liters\":0.35,\"trigger\":\"schedule\"}");

        verify(pumpService).recordFlowReceived(instanceId, 0.35, "schedule", null);
    }

    @Test
    void statusOff_forwardsTriggerReasonAndTimestamp() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/status",
                "{\"pump\":\"off\",\"trigger\":\"schedule\",\"reason\":\"completed\",\"ts\":1757764800}");

        verify(pumpService).recordPumpStatus(instanceId, "off", "schedule", "completed", 1757764800L, null);
    }

    @Test
    void statusRejected_recordsOutcome() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/status", "{\"pump\":\"rejected\",\"trigger\":\"manual\",\"reason\":\"busy\"}");

        verify(pumpService).recordPumpStatus(instanceId, "rejected", "manual", "busy", 0L, null);
    }

    @Test
    void statusBatteryLow_recordsNothing() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/status", "{\"battery\":\"low\"}");

        verifyNoInteractions(pumpService);
    }

    @Test
    void unknownPrefix_recordsNothing() {
        when(instanceRepository.findByMqttPrefix("unknown")).thenReturn(Optional.empty());

        handler().handle("unknown/sensors/moisture", "{\"sensor_0\":42}");

        verifyNoInteractions(sensorReadingService, pumpService, scheduleService);
    }

    @Test
    void readingsWithTimestamp_passTheDeviceTime() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/moisture", "{\"sensor_0\":42,\"ts\":1790751600}");
        handler().handle("plant/sensors/battery", "{\"soc\":78.1,\"voltage\":3.91,\"ts\":1790751600}");

        verify(sensorReadingService).recordMoisture(instanceId, 0, 42.0, 1790751600L);
        verify(sensorReadingService).recordBattery(instanceId, 78.1, 3.91, 1790751600L);
        verifyNoMoreInteractions(sensorReadingService);   // "ts" is not taken for a sensor
    }

    @Test
    void statusAndFlowWithId_passTheRequestId() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/status", "{\"pump\":\"on\",\"trigger\":\"manual\",\"id\":\"abc\",\"ts\":1790751600}");
        handler().handle("plant/sensors/flow", "{\"liters\":0.4,\"trigger\":\"manual\",\"id\":\"abc\"}");

        verify(pumpService).recordPumpStatus(instanceId, "on", "manual", null, 1790751600L, "abc");
        verify(pumpService).recordFlowReceived(instanceId, 0.4, "manual", "abc");
    }

    @Test
    void anyMessage_updatesLastSeen() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/sensors/battery", "{\"soc\":78.1,\"voltage\":3.91}");

        verify(instanceRepository).updateLastSeen(eq(instanceId), any(OffsetDateTime.class));
    }

    @Test
    void unknownPrefix_doesNotUpdateLastSeen() {
        when(instanceRepository.findByMqttPrefix("unknown")).thenReturn(Optional.empty());

        handler().handle("unknown/sensors/battery", "{\"soc\":1,\"voltage\":3}");

        verify(instanceRepository, never()).updateLastSeen(any(), any());
    }

    @Test
    void scheduleAck_recordsAcknowledgedVersion() {
        givenInstanceWithPrefix("plant");

        handler().handle("plant/schedule/ack", "{\"version\":7}");

        verify(scheduleService).recordAck(instanceId, 7);
        verifyNoInteractions(pumpService, sensorReadingService);
    }
}
