package com.gameio.gameresult.replay.minesweeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinesweeperReplayVerifierTest {
    private final MinesweeperReplayVerifier verifier = new MinesweeperReplayVerifier();

    @Test
    void keepsTheFirstRevealSafeAndDeterministic() {
        MinesweeperEngine first = new MinesweeperEngine(42);
        MinesweeperEngine second = new MinesweeperEngine(42);
        assertThat(first.reveal(40)).isTrue();
        assertThat(second.reveal(40)).isTrue();
        assertThat(first.state()).isEqualTo(second.state());
        assertThat(first.state().status()).isNotEqualTo("lost");
    }

    @Test
    void verifiesACompleteReplay() {
        MinesweeperEngine engine = new MinesweeperEngine(7_936);
        List<String> actions = new ArrayList<>();
        for (int index = 0; index < 81 && !engine.terminal(); index++) {
            if (engine.reveal(index)) {
                actions.add("R:" + index);
            }
        }
        VerifiedReplay replay = verifier.verify(7_936, actions);
        assertThat(replay.gameOver()).isTrue();
        assertThat(replay.score()).isEqualTo(engine.state().score());
    }

    @Test
    void rejectsDuplicateReveals() {
        assertThatThrownBy(() -> verifier.verify(1, List.of("R:0", "R:0")))
                .isInstanceOf(InvalidGameActionException.class);
    }
}
