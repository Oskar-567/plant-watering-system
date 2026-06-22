package com.plant_watering_system.server.controller;

import com.plant_watering_system.server.dto.LoginRequest;
import com.plant_watering_system.server.dto.LoginResponse;
import com.plant_watering_system.server.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final String appPassword;

    public AuthController(JwtTokenProvider tokenProvider, @Value("${app.password}") String appPassword) {
        this.tokenProvider = tokenProvider;
        this.appPassword = appPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!appPassword.equals(request.password())) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new LoginResponse(tokenProvider.generate()));
    }
}
