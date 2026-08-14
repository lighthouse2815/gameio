package com.gameio.multiplayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
class InMemoryActiveMatchStore implements ActiveMatchStore {
    private final Map<UUID, ActiveMatchCheckpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void save(ActiveMatchCheckpoint checkpoint) {
        checkpoints.put(checkpoint.room().roomId(), checkpoint);
    }

    @Override
    public Optional<ActiveMatchCheckpoint> find(UUID roomId) {
        return Optional.ofNullable(checkpoints.get(roomId));
    }

    @Override
    public void delete(UUID roomId) {
        checkpoints.remove(roomId);
    }
}
