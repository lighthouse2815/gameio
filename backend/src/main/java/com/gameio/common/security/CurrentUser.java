package com.gameio.common.security;

import com.gameio.common.error.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public UUID id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("AUTHENTICATION_REQUIRED", "Authentication is required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("INVALID_ACCESS_TOKEN", "Access token subject is invalid");
        }
    }
}
