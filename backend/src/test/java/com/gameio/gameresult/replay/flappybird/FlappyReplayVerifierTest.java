package com.gameio.gameresult.replay.flappybird;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlappyReplayVerifierTest {
    private final FlappyReplayVerifier verifier = new FlappyReplayVerifier();

    @Test
    void reproducesTheSeededInitialStateAndFixedTickPhysics() {
        FlappyState first = verifier.initialState(7_936);
        FlappyState second = verifier.initialState(7_936);

        assertThat(first).isEqualTo(second);
        assertThat(first.width()).isEqualTo(640);
        assertThat(first.height()).isEqualTo(480);
        assertThat(first.birdX()).isEqualTo(160);
        assertThat(first.birdY()).isEqualTo(24_000);
        assertThat(first.tickMs()).isEqualTo(50);
        assertThat(first.pipes()).extracting(FlappyPipeState::x)
                .containsExactly(520, 780, 1_040);

        FlappyEngine movingEngine = new FlappyEngine(7_936);
        movingEngine.step(FlappyAction.WAIT);
        assertThat(FlappyEngine.PIPE_SPEED).isEqualTo(5);
        assertThat(movingEngine.state().pipes()).extracting(FlappyPipeState::x)
                .containsExactly(515, 775, 1_035);
    }

    @Test
    void verifiesACompleteReplayAndDerivesItsMinimumDuration() {
        List<String> actions = terminalScoringReplay(42, 2);

        VerifiedReplay replay = verifier.verify(42, actions);

        assertThat(replay.gameOver()).isTrue();
        assertThat(replay.score()).isEqualTo(2);
        assertThat(replay.minimumDurationSeconds())
                .isEqualTo(Math.max(1, (actions.size() * FlappyEngine.TICK_MS + 999) / 1_000));
        assertThat((FlappyState) replay.finalState()).satisfies(state -> {
            assertThat(state.status()).isEqualTo("over");
            assertThat(state.tick()).isEqualTo(actions.size());
        });
    }

    @Test
    void rejectsUnsupportedAndTrailingActions() {
        assertThatThrownBy(() -> verifier.verify(1, List.of("DIVE")))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("unsupported");

        List<String> trailing = new ArrayList<>(terminalWaitReplay(42));
        trailing.add("FLAP");
        assertThatThrownBy(() -> verifier.verify(42, trailing))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("after game over");
    }

    private List<String> terminalWaitReplay(long seed) {
        FlappyEngine engine = new FlappyEngine(seed);
        List<String> actions = new ArrayList<>();
        while (!engine.terminal()) {
            engine.step(FlappyAction.WAIT);
            actions.add("WAIT");
        }
        return actions;
    }

    private List<String> terminalScoringReplay(long seed, long targetScore) {
        FlappyEngine engine = new FlappyEngine(seed);
        List<String> actions = new ArrayList<>();
        while (!engine.terminal() && actions.size() < 1_000) {
            FlappyState state = engine.state();
            FlappyPipeState nextPipe = state.pipes().stream()
                    .filter(pipe -> pipe.x() + FlappyEngine.PIPE_WIDTH
                            >= state.birdX() - FlappyEngine.BIRD_HALF_WIDTH)
                    .findFirst()
                    .orElseThrow();
            boolean shouldFlap = state.score() < targetScore
                    && state.birdY() / 100 > nextPipe.gapCenter() - 20
                    && state.birdVelocity() > 50;
            FlappyAction action = shouldFlap ? FlappyAction.FLAP : FlappyAction.WAIT;
            engine.step(action);
            actions.add(action.name());
        }
        assertThat(engine.terminal()).isTrue();
        return actions;
    }
}
