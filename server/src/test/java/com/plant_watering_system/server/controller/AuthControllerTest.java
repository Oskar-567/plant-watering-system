package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.security.JwtAuthFilter;
import com.plant_watering_system.server.security.JwtTokenProvider;
import com.plant_watering_system.server.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-min-32-chars-long-placeholder",
        "jwt.expiration-ms=3600000",
        "app.password=test-password"
})
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized());
    }
}
