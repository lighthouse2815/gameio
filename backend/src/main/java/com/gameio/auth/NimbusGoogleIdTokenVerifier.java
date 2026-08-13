package com.gameio.auth;

import com.nimbusds.jose.RemoteKeySourceException;
import com.gameio.common.error.ServiceUnavailableException;
import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.GoogleIdentityProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

final class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
    private final GoogleIdentityProperties properties;
    private final JwtDecoder decoder;

    NimbusGoogleIdTokenVerifier(GoogleIdentityProperties properties, JwtDecoder decoder) {
        this.properties = properties;
        this.decoder = decoder;
    }

    @Override
    public VerifiedGoogleIdentity verify(String credential) {
        if (!properties.configured()) {
            throw new ServiceUnavailableException(
                    "GOOGLE_AUTH_NOT_CONFIGURED", "Google sign-in is not configured");
        }

        Jwt token;
        try {
            token = decoder.decode(credential);
        } catch (JwtException exception) {
            if (causedByRemoteKeySourceFailure(exception)) {
                throw new ServiceUnavailableException(
                        "GOOGLE_IDENTITY_UNAVAILABLE", "Google identity verification is temporarily unavailable");
            }
            throw invalidToken();
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }

        String subject;
        String email;
        Boolean emailVerified;
        try {
            subject = trimmed(token.getSubject());
            email = trimmed(token.getClaimAsString("email"));
            emailVerified = token.getClaimAsBoolean("email_verified");
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
        if (subject == null || subject.length() > 255
                || email == null || email.length() > 254 || !hasValidEmailShape(email)
                || !Boolean.TRUE.equals(emailVerified)) {
            throw invalidToken();
        }
        return new VerifiedGoogleIdentity(subject, email);
    }

    private boolean causedByRemoteKeySourceFailure(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof RemoteKeySourceException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean hasValidEmailShape(String email) {
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
                && email.chars().noneMatch(Character::isWhitespace);
    }

    private String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException("INVALID_GOOGLE_ID_TOKEN", "Google ID token is invalid");
    }
}
