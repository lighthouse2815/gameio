package com.gameio.friend;

public record FriendPresence(
        boolean online,
        String currentGameSlug,
        String currentGameName
) {
    public static FriendPresence offline() {
        return new FriendPresence(false, null, null);
    }
}
