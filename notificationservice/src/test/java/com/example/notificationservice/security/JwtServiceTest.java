package com.example.notificationservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;

public class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-key-that-is-at-least-32-characters-long-for-hs256",
                3600000L);
    }

    @Test
    void generateAndParseToken_roundTripsCorrectly() {
        String token = jwtService.generateToken("user@test.com", "ROLE_TENANT_ADMIN", 5L);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("role")).isEqualTo("ROLE_TENANT_ADMIN");
        assertThat(claims.get("tenantId")).isEqualTo(5);
    }

    @Test
    void platformAdminToken_hasNoTenantIdClaim() {
        String token = jwtService.generateToken("admin@test.com", "ROLE_PLATFORM_ADMIN", null);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.get("tenantId")).isNull();
    }

    @Test
    void extractEmail_returnsSubject() {
        String token = jwtService.generateToken("user@test.com", "ROLE_TENANT_ADMIN", 1L);

        assertThat(jwtService.extractEmail(token)).isEqualTo("user@test.com");
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateToken("user@test.com", "ROLE_TENANT_ADMIN", 1L);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.valid.token")).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        JwtService shortLivedService = new JwtService(
                "test-secret-key-that-is-at-least-32-characters-long-for-hs256", -1000L);
        String expiredToken = shortLivedService.generateToken("user@test.com", "ROLE_TENANT_ADMIN", 1L);

        assertThat(shortLivedService.isTokenValid(expiredToken)).isFalse();
    }
}