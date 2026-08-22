package com.ticketflow.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-secret-key-at-least-32-bytes-long-for-hs256",
            3600000L,
            604800000L
    );

    @Test
    void generatesValidAccessTokenWithExpectedClaims() {
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.generateAccessToken(userId, "user@example.com", "CUSTOMER");

        assertThat(jwtProvider.isValid(token)).isTrue();

        Claims claims = jwtProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtProvider.isValid("not-a-real-token")).isFalse();
    }

    @Test
    void refreshTokensAreUniqueAndHighEntropy() {
        String t1 = jwtProvider.generateOpaqueRefreshToken();
        String t2 = jwtProvider.generateOpaqueRefreshToken();

        assertThat(t1).isNotEqualTo(t2);
        assertThat(t1.length()).isGreaterThanOrEqualTo(80);
    }
}
