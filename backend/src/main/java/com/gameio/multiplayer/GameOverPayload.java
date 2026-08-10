package com.gameio.multiplayer;

import com.gameio.gameresult.multiplayer.PlayerProgression;
import java.util.List;
import java.util.UUID;

public record GameOverPayload(
        UUID matchId,
        Object finalState,
        List<PlayerProgression> progression
) {
}
