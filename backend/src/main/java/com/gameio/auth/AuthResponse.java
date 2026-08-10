package com.gameio.auth;

import com.gameio.user.UserResponse;
import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessExpiresAt,
        UserResponse user
) {
}
