package com.gameio.gameresult.replay.flappybird;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FlappyReplayVerifier implements GameReplayVerifier {
    @Override
    public String gameSlug() {
        return "flappy-bird";
    }

    @Override
    public FlappyState initialState(long seed) {
        return new FlappyEngine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        FlappyEngine engine = new FlappyEngine(seed);
        long minimumDurationMillis = 0;
        for (String action : actions) {
            if (engine.terminal()) {
                throw new InvalidGameActionException("Flappy Bird replay contains actions after game over");
            }
            FlappyAction parsed;
            try {
                parsed = FlappyAction.valueOf(action == null ? "" : action.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidGameActionException("Replay contains an unsupported Flappy Bird action");
            }
            engine.step(parsed);
            minimumDurationMillis += engine.tickMs();
        }
        FlappyState state = engine.state();
        int minimumDurationSeconds = Math.max(1,
                Math.toIntExact((minimumDurationMillis + 999) / 1_000));
        return new VerifiedReplay(state.score(), engine.terminal(), 0, state, minimumDurationSeconds);
    }
}
