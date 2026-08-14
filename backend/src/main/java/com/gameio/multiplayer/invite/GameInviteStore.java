package com.gameio.multiplayer.invite;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface GameInviteStore {
    void save(GameInvite invite, Duration ttl);

    Optional<GameInvite> consume(UUID inviteId);
}
