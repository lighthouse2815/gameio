package com.gameio.gameresult.replay.minesweeper;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MinesweeperReplayVerifier implements GameReplayVerifier {
    private static final Pattern REVEAL = Pattern.compile("R:(\\d{1,2})");

    @Override
    public String gameSlug() {
        return "minesweeper";
    }

    @Override
    public MinesweeperState initialState(long seed) {
        return new MinesweeperEngine(seed).state();
    }

    @Override
    public VerifiedReplay verify(long seed, List<String> actions) {
        MinesweeperEngine engine = new MinesweeperEngine(seed);
        for (String action : actions) {
            if (engine.terminal()) {
                throw new InvalidGameActionException("Minesweeper replay contains actions after game over");
            }
            Matcher matcher = REVEAL.matcher(action == null ? "" : action);
            if (!matcher.matches() || !engine.reveal(Integer.parseInt(matcher.group(1)))) {
                throw new InvalidGameActionException("Replay contains an unsupported Minesweeper action");
            }
        }
        MinesweeperState state = engine.state();
        int minimumDurationSeconds = Math.max(1, (actions.size() + 29) / 30);
        return new VerifiedReplay(state.score(), engine.terminal(), 0, state, minimumDurationSeconds);
    }
}
