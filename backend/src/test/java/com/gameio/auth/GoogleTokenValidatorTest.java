package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleTokenValidatorTest {
    private static final String CLIENT_ID = "gameio.apps.googleusercontent.com";
    private final GoogleTokenValidator validator = new GoogleTokenValidator(CLIENT_ID);

    @Test
    void acceptsBothGoogleIssuersForTheConfiguredAudience() {
        assertThat(validator.validate(token("accounts.google.com", List.of(CLIENT_ID), null)).hasErrors())
                .isFalse();
        assertThat(validator.validate(token("https://accounts.google.com", List.of(CLIENT_ID), null)).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsWrongIssuerAudienceAndAuthorizedParty() {
        assertInvalid(token("https://attacker.example", List.of(CLIENT_ID), null));
        assertInvalid(token("https://accounts.google.com", List.of("other-client"), null));
        assertInvalid(token("https://accounts.google.com", List.of(CLIENT_ID, "other-client"), "other-client"));
        assertInvalid(tokenWithoutAudience("https://accounts.google.com"));
    }

    @Test
    void acceptsMultipleAudiencesOnlyWhenAuthorizedPartyMatches() {
        OAuth2TokenValidatorResult result = validator.validate(
                token("https://accounts.google.com", List.of(CLIENT_ID, "other-client"), CLIENT_ID));
        assertThat(result.hasErrors()).isFalse();
    }

    private void assertInvalid(Jwt token) {
        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    private Jwt token(String issuer, List<String> audience, String authorizedParty) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        if (authorizedParty != null) {
            claims.put("azp", authorizedParty);
        }
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "RS256"), claims);
    }

    private Jwt tokenWithoutAudience(String issuer) {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "RS256"), Map.of("iss", issuer));
    }
}
