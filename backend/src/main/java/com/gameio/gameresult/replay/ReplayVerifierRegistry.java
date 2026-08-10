package com.gameio.gameresult.replay;

import com.gameio.common.error.InvalidGameActionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReplayVerifierRegistry {
    private final Map<String, GameReplayVerifier> verifiers;

    public ReplayVerifierRegistry(List<GameReplayVerifier> verifierList) {
        Map<String, GameReplayVerifier> indexed = new HashMap<>();
        for (GameReplayVerifier verifier : verifierList) {
            if (indexed.put(verifier.gameSlug(), verifier) != null) {
                throw new IllegalStateException("Duplicate replay verifier for " + verifier.gameSlug());
            }
        }
        this.verifiers = Map.copyOf(indexed);
    }

    public GameReplayVerifier require(String gameSlug) {
        GameReplayVerifier verifier = verifiers.get(gameSlug);
        if (verifier == null) {
            throw new InvalidGameActionException("This game does not support replay-verified result submission");
        }
        return verifier;
    }
}
