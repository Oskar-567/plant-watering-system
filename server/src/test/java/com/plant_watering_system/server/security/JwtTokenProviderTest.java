package com.plant_watering_system.server.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-min-32-chars-long-placeholder",
            3600000L
    );

    @Test
    void generatedTokenIsValid() {
        assertTrue(provider.validate(provider.generate()));
    }

    @Test
    void tamperedTokenIsInvalid() {
        assertFalse(provider.validate(provider.generate() + "tampered"));
    }

    @Test
    void randomStringIsInvalid() {
        assertFalse(provider.validate("not-a-jwt"));
    }
}
