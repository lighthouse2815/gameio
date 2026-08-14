package com.gameio.gameresult.replay.breakout;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class BreakoutReplayVerifier implements GameReplayVerifier {
    @Override
    public String gameSlug() {
        return "breakout";
    }

    @Override
    public BreakoutState initialState(long seed) {
        return new BreakoutEngine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        BreakoutEngine engine = new BreakoutEngine(seed);
        int ticks = 0;
        for (String rawAction : actions) {
            String action = rawAction == null ? "" : rawAction.toUpperCase(Locale.ROOT);
            if (!action.matches("[LRN]{1,3}")) {
                throw new InvalidGameActionException("Replay contains an unsupported Breakout action group");
            }
            for (int index = 0; index < action.length(); index++) {
                if (engine.terminal()) {
                    throw new InvalidGameActionException("Breakout replay contains actions after game over");
                }
                engine.step(action.charAt(index));
                ticks += 1;
            }
        }
        BreakoutState state = engine.state();
        int minimumDurationSeconds = Math.max(1, (ticks + BreakoutEngine.TICKS_PER_SECOND - 1)
                / BreakoutEngine.TICKS_PER_SECOND);
        return new VerifiedReplay(state.score(), engine.terminal(), 0, state, minimumDurationSeconds);
    }
}
