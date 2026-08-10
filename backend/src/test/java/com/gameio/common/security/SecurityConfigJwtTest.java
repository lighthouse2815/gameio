package com.gameio.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class SecurityConfigJwtTest {
    @Test
    void rejectsCorrectlySignedTokenFromWrongIssuer() {
        JwtProperties properties = new JwtProperties("gameio",
                "test-secret-with-at-least-thirty-two-characters-long", Duration.ofMinutes(5), Duration.ofDays(1));
        SecurityConfig config = new SecurityConfig();
        JwtEncoder encoder = config.jwtEncoder(properties);
        JwtDecoder decoder = config.jwtDecoder(properties);
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("forged-issuer")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }
}
