package com.gameio.gameresult.replay.snake;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SnakeReplayVerifier implements GameReplayVerifier {
    @Override
    public String gameSlug() {
        return "snake";
    }

    @Override
    public SnakeState initialState(long seed) {
        return new SnakeEngine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        SnakeEngine engine = new SnakeEngine(seed);
        long minimumDurationMillis = 0;
        for (String action : actions) {
            if (engine.terminal()) {
                throw new InvalidGameActionException("Snake replay contains actions after game over");
            }
            SnakeDirection direction;
            try {
                direction = SnakeDirection.valueOf(action.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidGameActionException("Replay contains an unsupported Snake action");
            }
            minimumDurationMillis += engine.tickMs();
            try {
                engine.step(direction);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new InvalidGameActionException(exception.getMessage());
            }
        }
        SnakeState state = engine.state();
        int minimumDurationSeconds = Math.max(1,
                Math.toIntExact((minimumDurationMillis + 999) / 1_000));
        return new VerifiedReplay(state.score(), engine.terminal(), 0, state, minimumDurationSeconds);
    }
}
