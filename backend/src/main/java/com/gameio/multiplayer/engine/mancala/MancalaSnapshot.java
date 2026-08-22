package com.gameio.multiplayer.engine.mancala;

import java.util.List;
import java.util.UUID;

public record MancalaSnapshot(
        long sequence,
        List<Integer> pits,
        List<Integer> scores,
        List<Integer> legalPits,
        Integer lastPit,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw
) {
    public MancalaSnapshot {
        pits = List.copyOf(pits);
        scores = List.copyOf(scores);
        legalPits = List.copyOf(legalPits);
    }
}
