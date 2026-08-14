package com.gameio.gameresult.replay.memorymatch;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MemoryMatchReplayVerifier implements GameReplayVerifier {
    private static final Pattern SELECT = Pattern.compile("S:(\\d{1,2})");

    @Override
    public String gameSlug() {
        return "memory-match";
    }

    @Override
    public MemoryState initialState(long seed) {
        return new MemoryMatchEngine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        MemoryMatchEngine engine = new MemoryMatchEngine(seed);
        for (String action : actions) {
            if (engine.terminal()) {
                throw new InvalidGameActionException("Memory Match replay contains actions after game over");
            }
            Matcher matcher = SELECT.matcher(action == null ? "" : action);
            if (!matcher.matches() || !engine.select(Integer.parseInt(matcher.group(1)))) {
                throw new InvalidGameActionException("Replay contains an unsupported Memory Match action");
            }
        }
        MemoryState state = engine.state();
        int minimumDurationSeconds = Math.max(1, (actions.size() + 29) / 30);
        return new VerifiedReplay(state.score(), engine.terminal(), 0, state, minimumDurationSeconds);
    }
}
