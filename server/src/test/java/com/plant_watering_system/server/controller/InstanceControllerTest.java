package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.InstanceResponse;
import com.plant_watering_system.server.security.JwtAuthFilter;
import com.plant_watering_system.server.security.JwtTokenProvider;
import com.plant_watering_system.server.security.SecurityConfig;
import com.plant_watering_system.server.service.InstanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
}
