package com.gameio.matchmaking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingStore {
    void enqueue(MatchmakingTicket ticket);

    Optional<MatchmakingTicket> findByUser(UUID userId);

    List<MatchmakingTicket> takeOldest(UUID gameId, int count);

    void saveMatched(MatchmakingTicket ticket);

    void remove(UUID userId);
}
