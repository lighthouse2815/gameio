package com.gameio.gameresult.replay.breakout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreakoutReplayVerifierTest {
    private final BreakoutReplayVerifier verifier = new BreakoutReplayVerifier();

    @Test
    void createsDeterministicInitialState() {
        assertThat(verifier.initialState(7_936)).isEqualTo(verifier.initialState(7_936));
        assertThat(verifier.initialState(7_936).bricks()).hasSize(28).allMatch(Boolean::booleanValue);
    }

    @Test
    void verifiesACompleteGroupedReplay() {
        BreakoutEngine engine = new BreakoutEngine(42);
        List<String> actions = new ArrayList<>();
        StringBuilder group = new StringBuilder(3);
        for (int tick = 0; tick < 50_000 && !engine.terminal(); tick++) {
            engine.step('L');
            group.append('L');
            if (group.length() == 3 || engine.terminal()) {
                actions.add(group.toString());
                group.setLength(0);
            }
        }
        assertThat(engine.terminal()).isTrue();

        VerifiedReplay replay = verifier.verify(42, actions);
        assertThat(replay.gameOver()).isTrue();
        assertThat(replay.score()).isEqualTo(engine.state().score());
    }

    @Test
    void rejectsMalformedActionGroups() {
        assertThatThrownBy(() -> verifier.verify(1, List.of("LEFT")))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("unsupported");
    }
}
