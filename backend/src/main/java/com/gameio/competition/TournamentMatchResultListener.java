package com.gameio.competition;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TournamentMatchResultListener {
    private final TournamentService tournaments;

    TournamentMatchResultListener(TournamentService tournaments) {
        this.tournaments = tournaments;
    }

    @EventListener
    void record(TournamentMatchCompletedEvent event) {
        tournaments.recordMatchResult(event.roomId(), event.outcomes());
    }
}

