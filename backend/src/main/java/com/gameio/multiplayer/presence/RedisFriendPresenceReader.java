package com.gameio.multiplayer.presence;

import com.gameio.friend.FriendPresence;
import com.gameio.friend.FriendPresenceReader;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Primary
@Component
@Profile("!test")
public class RedisFriendPresenceReader implements FriendPresenceReader {
    private final PresenceStore presenceStore;

    public RedisFriendPresenceReader(PresenceStore presenceStore) {
        this.presenceStore = presenceStore;
    }

    @Override
    public Map<UUID, FriendPresence> read(Set<UUID> userIds) {
        return presenceStore.read(userIds).entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> new FriendPresence(entry.getValue().online(), entry.getValue().gameSlug(),
                        entry.getValue().gameName())));
    }
}
