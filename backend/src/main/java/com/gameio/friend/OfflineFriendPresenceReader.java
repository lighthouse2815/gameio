package com.gameio.friend;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OfflineFriendPresenceReader implements FriendPresenceReader {
    @Override
    public Map<UUID, FriendPresence> read(Set<UUID> userIds) {
        return userIds.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), ignored -> FriendPresence.offline()));
    }
}
