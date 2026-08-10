package com.gameio.friend;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boundary between durable friendship data and replaceable realtime presence storage.
 * Implementations return one presence value for every requested user id.
 */
public interface FriendPresenceReader {
    Map<UUID, FriendPresence> read(Set<UUID> userIds);
}
