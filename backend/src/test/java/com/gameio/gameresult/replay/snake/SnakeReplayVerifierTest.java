package com.gameio.gameresult.replay.snake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnakeReplayVerifierTest {
    private final SnakeReplayVerifier verifier = new SnakeReplayVerifier();

    @Test
    void reproducesSeededFoodAndDerivesScoreFromEverySimulationTick() {
        SnakeState initial = verifier.initialState(7_936);

        assertThat(initial.body()).containsExactly(
                new SnakePoint(10, 7), new SnakePoint(9, 7), new SnakePoint(8, 7));
        assertThat(initial.food()).isEqualTo(new SnakePoint(11, 7));
        assertThat(initial.direction()).isEqualTo("right");

        VerifiedReplay replay = verifier.verify(7_936, Collections.nCopies(10, "RIGHT"));

        assertThat(replay.gameOver()).isTrue();
        assertThat(replay.score()).isEqualTo(10);
        assertThat(replay.minimumDurationSeconds()).isEqualTo(2);
        assertThat((SnakeState) replay.finalState()).satisfies(state -> {
            assertThat(state.status()).isEqualTo("over");
            assertThat(state.tickMs()).isEqualTo(144);
        });
    }

    @Test
    void rejectsReverseDirectionsAndActionsAfterTheTerminalTick() {
        assertThatThrownBy(() -> verifier.verify(1, List.of("LEFT")))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("reverse");

        List<String> trailingAction = new ArrayList<>(Collections.nCopies(10, "RIGHT"));
        trailingAction.add("UP");
        assertThatThrownBy(() -> verifier.verify(7_936, trailingAction))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("after game over");
    }
}
