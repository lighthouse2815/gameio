package com.gameio.multiplayer.presence;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PresenceStore {
    void online(UUID userId, UUID roomId, String gameSlug, String gameName);

    void offline(UUID userId);

    Map<UUID, PresenceState> read(Set<UUID> userIds);
}
