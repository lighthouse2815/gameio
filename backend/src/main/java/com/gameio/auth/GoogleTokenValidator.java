package com.gameio.auth;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class GoogleTokenValidator implements OAuth2TokenValidator<Jwt> {
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "Google ID token issuer or audience is invalid", null);

    private final String clientId;

    GoogleTokenValidator(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String issuer = token.getClaimAsString("iss");
        List<String> audience = token.getAudience();
        if (!ISSUERS.contains(issuer) || audience == null || !audience.contains(clientId)) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        if (audience.size() > 1 && !clientId.equals(token.getClaimAsString("azp"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
