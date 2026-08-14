package com.gameio.multiplayer;

import java.util.Optional;
import java.util.UUID;

public interface ActiveMatchStore {
    void save(ActiveMatchCheckpoint checkpoint);

    Optional<ActiveMatchCheckpoint> find(UUID roomId);

    void delete(UUID roomId);
}
