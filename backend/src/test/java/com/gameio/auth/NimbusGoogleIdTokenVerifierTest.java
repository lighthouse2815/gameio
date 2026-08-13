package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.RemoteKeySourceException;
import com.gameio.common.error.ServiceUnavailableException;
import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.GoogleIdentityProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class NimbusGoogleIdTokenVerifierTest {
    private static final GoogleIdentityProperties CONFIGURED = new GoogleIdentityProperties(
            "gameio.apps.googleusercontent.com", "https://www.googleapis.com/oauth2/v3/certs");

    @Test
    void extractsStableIdentityOnlyFromVerifiedEmailClaims() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("credential")).thenReturn(token(
                "google-subject", "Player@gmail.com", true));

        VerifiedGoogleIdentity identity = new NimbusGoogleIdTokenVerifier(CONFIGURED, decoder)
                .verify("credential");

        assertThat(identity).isEqualTo(new VerifiedGoogleIdentity(
                "google-subject", "Player@gmail.com"));
    }

    @Test
    void rejectsDecoderFailuresAndUnverifiedOrMalformedEmailClaims() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("invalid-signature")).thenThrow(new JwtException("invalid"));
        when(decoder.decode("unverified")).thenReturn(token(
                "google-subject", "player@gmail.com", false));
        when(decoder.decode("malformed-email")).thenReturn(token(
                "google-subject", "not-an-email", true));
        when(decoder.decode("malformed-verification-claim")).thenReturn(token(
                "google-subject", "player@gmail.com", "definitely"));
        NimbusGoogleIdTokenVerifier verifier = new NimbusGoogleIdTokenVerifier(CONFIGURED, decoder);

        assertInvalid(verifier, "invalid-signature");
        assertInvalid(verifier, "unverified");
        assertInvalid(verifier, "malformed-email");
        assertInvalid(verifier, "malformed-verification-claim");
    }

    @Test
    void reportsControlledUnavailableErrorBeforeDecodingWhenClientIdIsMissing() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        GoogleIdentityProperties disabled = new GoogleIdentityProperties(
                "", "https://www.googleapis.com/oauth2/v3/certs");

        assertThatThrownBy(() -> new NimbusGoogleIdTokenVerifier(disabled, decoder).verify("credential"))
                .isInstanceOfSatisfying(ServiceUnavailableException.class,
                        exception -> assertThat(exception.code()).isEqualTo("GOOGLE_AUTH_NOT_CONFIGURED"));
        verify(decoder, never()).decode("credential");
    }

    @Test
    void reportsRemoteJwkFailuresAsTemporarilyUnavailable() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("credential")).thenThrow(new JwtException(
                "Unable to obtain Google keys",
                new RemoteKeySourceException("Google JWK endpoint is unavailable", new java.io.IOException("timeout"))));

        assertThatThrownBy(() -> new NimbusGoogleIdTokenVerifier(CONFIGURED, decoder).verify("credential"))
                .isInstanceOfSatisfying(ServiceUnavailableException.class,
                        exception -> assertThat(exception.code()).isEqualTo("GOOGLE_IDENTITY_UNAVAILABLE"));
    }

    private void assertInvalid(NimbusGoogleIdTokenVerifier verifier, String credential) {
        assertThatThrownBy(() -> verifier.verify(credential))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_GOOGLE_ID_TOKEN"));
    }

    private Jwt token(String subject, String email, Object verified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", verified);
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "RS256"), claims);
    }
}
