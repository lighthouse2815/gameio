package com.gameio.matchmaking;

import com.gameio.common.error.NotFoundException;

public final class MatchmakingNotFoundException extends NotFoundException {
    public MatchmakingNotFoundException() {
        super("MATCHMAKING_TICKET_NOT_FOUND", "No active matchmaking ticket was found");
    }
}
