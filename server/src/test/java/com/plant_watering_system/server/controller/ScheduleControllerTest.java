package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.ScheduleEntryResponse;
import com.plant_watering_system.server.dto.ScheduleRequest;
import com.plant_watering_system.server.dto.ScheduleResponse;
import com.plant_watering_system.server.security.JwtAuthFilter;
import com.plant_watering_system.server.security.JwtTokenProvider;
import com.plant_watering_system.server.security.SecurityConfig;
import com.plant_watering_system.server.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-min-32-chars-long-placeholder",
        "jwt.expiration-ms=3600000",
        "app.password=test-password"
})
class ScheduleControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ScheduleService scheduleService;

    private static final String VALID_BODY = """
            {"entries":[{"time":"12:00","durationSeconds":600,"days":["MONDAY","FRIDAY"]}]}
            """;

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/instances/{id}/schedule", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReturnsSchedule() throws Exception {
        var id = UUID.randomUUID();
        given(scheduleService.get(id)).willReturn(new ScheduleResponse(3, 3, true, OffsetDateTime.now(),
                List.of(new ScheduleEntryResponse("12:00", 600, List.of(DayOfWeek.MONDAY)))));

        mvc.perform(get("/instances/{id}/schedule", id).with(user("test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.synced").value(true))
                .andExpect(jsonPath("$.entries[0].time").value("12:00"))
                .andExpect(jsonPath("$.entries[0].days[0]").value("MONDAY"));
    }

    @Test
    void putValidScheduleReturns200() throws Exception {
        var id = UUID.randomUUID();
        given(scheduleService.replace(eq(id), any(ScheduleRequest.class)))
                .willReturn(new ScheduleResponse(4, 3, false, OffsetDateTime.now(), List.of()));

        mvc.perform(put("/instances/{id}/schedule", id)
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.synced").value(false));
    }

    @Test
    void putInvalidScheduleReturns400() throws Exception {
        var id = UUID.randomUUID();
        String entry = "{\"time\":\"12:00\",\"durationSeconds\":60,\"days\":[\"MONDAY\"]}";
        List<String> invalidBodies = List.of(
                "{}",
                "{\"entries\":[{\"time\":\"24:00\",\"durationSeconds\":600,\"days\":[\"MONDAY\"]}]}",
                "{\"entries\":[{\"time\":\"7:00\",\"durationSeconds\":600,\"days\":[\"MONDAY\"]}]}",
                "{\"entries\":[{\"time\":\"12:00\",\"durationSeconds\":601,\"days\":[\"MONDAY\"]}]}",
                "{\"entries\":[{\"time\":\"12:00\",\"durationSeconds\":0,\"days\":[\"MONDAY\"]}]}",
                "{\"entries\":[{\"time\":\"12:00\",\"durationSeconds\":600,\"days\":[]}]}",
                "{\"entries\":[" + String.join(",", java.util.Collections.nCopies(9, entry)) + "]}"
        );

        for (String body : invalidBodies) {
            mvc.perform(put("/instances/{id}/schedule", id)
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(scheduleService);
    }
}
