package com.gameio.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String avatarUrl,
        int level,
        long exp,
        Instant createdAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(),
                user.getLevel(), user.getExp(), user.getCreatedAt());
    }
}
