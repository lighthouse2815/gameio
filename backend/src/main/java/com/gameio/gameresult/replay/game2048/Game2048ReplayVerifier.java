package com.gameio.gameresult.replay.game2048;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Game2048ReplayVerifier implements GameReplayVerifier {
    @Override
    public String gameSlug() {
        return "2048";
    }

    @Override
    public Game2048State initialState(long seed) {
        return new Game2048Engine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        Game2048Engine engine = new Game2048Engine(seed);
        for (String action : actions) {
            try {
                engine.move(MoveDirection.valueOf(action.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new InvalidGameActionException("Replay contains an unsupported 2048 action");
            }
        }
        Game2048State state = engine.state();
        int minimumDurationSeconds = Math.max(1, (actions.size() + 29) / 30);
        return new VerifiedReplay(state.score(), state.gameOver(), state.highestValue(), state,
                minimumDurationSeconds);
    }
}
