package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.dto.MoisturePoint;
import com.plant_watering_system.server.security.JwtAuthFilter;
import com.plant_watering_system.server.security.JwtTokenProvider;
import com.plant_watering_system.server.security.SecurityConfig;
import com.plant_watering_system.server.service.SensorReadingService;
import com.plant_watering_system.server.service.InstanceService;
import com.plant_watering_system.server.service.PumpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstanceController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-min-32-chars-long-placeholder",
        "jwt.expiration-ms=3600000",
        "app.password=test-password"
})
class InstanceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InstanceService service;

    @MockitoBean
    PumpService pumpService;

    @MockitoBean
    SensorReadingService sensorReadingService;

    @Test
    void getAllWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/instances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInstanceReturns201() throws Exception {
        var id = UUID.randomUUID();
        given(service.create(any())).willReturn(
                new InstanceResponse(id, "Balkon", "plant/balkon", false, false, 1, null, null, OffsetDateTime.now())
        );

        mvc.perform(post("/instances")
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Balkon","mqttPrefix":"plant/balkon","hasPump":false,"hasBattery":false,"sensorCount":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Balkon"));
    }

    @Test
    void pumpStartWithDurationReturns204() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post("/instances/{id}/pump/start", id)
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\":600}"))
                .andExpect(status().isNoContent());

        verify(pumpService).start(id, 600);
    }

    @Test
    void pumpStartWithMissingOrOutOfRangeDurationReturns400() throws Exception {
        var id = UUID.randomUUID();

        for (String body : List.of("{}", "{\"durationSeconds\":0}", "{\"durationSeconds\":601}")) {
            mvc.perform(post("/instances/{id}/pump/start", id)
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(pumpService);
    }

    @Test
    void getMoistureWithDayRangeReturnsReadings() throws Exception {
        var id = UUID.randomUUID();
        given(sensorReadingService.getMoisture(id, Duration.ofDays(7))).willReturn(
                List.of(new MoisturePoint(Instant.parse("2026-09-13T10:15:00Z"), 1, 55.0))
        );

        mvc.perform(get("/instances/{id}/moisture", id).param("range", "7d").with(user("test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorIndex").value(1))
                .andExpect(jsonPath("$[0].percent").value(55.0));
    }

    @Test
    void getMoistureWithMinuteRangeQueriesMinutes() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(get("/instances/{id}/moisture", id).param("range", "30m").with(user("test")))
                .andExpect(status().isOk());

        verify(sensorReadingService).getMoisture(id, Duration.ofMinutes(30));
    }

    @Test
    void getBatteryWithoutRangeDefaultsTo24Hours() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(get("/instances/{id}/battery", id).with(user("test")))
                .andExpect(status().isOk());

        verify(sensorReadingService).getBattery(id, Duration.ofHours(24));
    }

    @Test
    void getMoistureWithInvalidRangeReturns400() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(get("/instances/{id}/moisture", id).param("range", "abc").with(user("test")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/instances/{id}/moisture", id).param("range", "24h)").with(user("test")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/instances/{id}/battery", id).param("range", "0h").with(user("test")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sensorReadingService);
    }

    @Test
    void getMoistureWithThirtyDayRangeIsAllowed() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(get("/instances/{id}/moisture", id).param("range", "30d").with(user("test")))
                .andExpect(status().isOk());
        mvc.perform(get("/instances/{id}/moisture", id).param("range", "720h").with(user("test")))
                .andExpect(status().isOk());

        // Duration equality is by length, so 30d and 720h are the same Duration
        verify(sensorReadingService, times(2)).getMoisture(id, Duration.ofDays(30));
    }

    @Test
    void getSensorHistoryWithRangeAboveThirtyDaysReturns400() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(get("/instances/{id}/moisture", id).param("range", "31d").with(user("test")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/instances/{id}/battery", id).param("range", "721h").with(user("test")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sensorReadingService);
    }
}
