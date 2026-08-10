package com.gameio.friend;

import com.gameio.user.UserAccount;
import java.util.UUID;

public record FriendResponse(
        UUID id,
        String username,
        String avatarUrl,
        int level,
        boolean online,
        String currentGameSlug,
        String currentGameName
) {
    static FriendResponse from(UserAccount user, FriendPresence presence) {
        return new FriendResponse(
                user.getId(), user.getUsername(), user.getAvatarUrl(), user.getLevel(),
                presence.online(), presence.currentGameSlug(), presence.currentGameName());
    }
}
