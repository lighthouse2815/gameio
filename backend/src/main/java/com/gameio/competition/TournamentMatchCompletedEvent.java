package com.gameio.competition;

import com.gameio.gameresult.multiplayer.AuthoritativePlayerOutcome;
import java.util.List;
import java.util.UUID;

public record TournamentMatchCompletedEvent(UUID roomId, List<AuthoritativePlayerOutcome> outcomes) {
    public TournamentMatchCompletedEvent {
        outcomes = List.copyOf(outcomes);
    }
}

