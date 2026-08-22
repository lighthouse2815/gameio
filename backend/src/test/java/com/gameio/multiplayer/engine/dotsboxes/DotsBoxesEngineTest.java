package com.gameio.multiplayer.engine.dotsboxes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DotsBoxesEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void completesTwoAdjacentBoxesAndKeepsTheScoringTurn() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DotsBoxesEngine engine = new DotsBoxesEngine(List.of(first, second));

        draw(engine, first, "H", 0, 0);
        draw(engine, second, "H", 1, 0);
        draw(engine, first, "V", 0, 0);
        draw(engine, second, "H", 0, 1);
        draw(engine, first, "H", 1, 1);
        draw(engine, second, "V", 0, 2);
        DotsBoxesSnapshot captured = (DotsBoxesSnapshot) draw(engine, first, "V", 0, 1).snapshot();

        assertThat(captured.scores()).containsExactly(2, 0);
        assertThat(captured.boxes().getFirst()).startsWith("R", "R");
        assertThat(captured.currentTurnPlayerId()).isEqualTo(first);
        assertThat(captured.lastEdge()).isEqualTo(new EdgeMove("V", 0, 1));

        assertThatThrownBy(() -> draw(engine, first, "V", 0, 1))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("already drawn");
    }

    @Test
    void restoresEdgesScoresAndTurnBeforeContinuing() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DotsBoxesEngine original = new DotsBoxesEngine(List.of(first, second));
        draw(original, first, "H", 0, 0);
        draw(original, second, "V", 0, 0);

        DotsBoxesEngine restored = new DotsBoxesEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        DotsBoxesSnapshot next = (DotsBoxesSnapshot) draw(restored, first, "V", 0, 1).snapshot();
        assertThat(next.sequence()).isEqualTo(3);
        assertThat(next.currentTurnPlayerId()).isEqualTo(second);
    }

    @Test
    void legalEdgePlayReachesTerminalScoresAndCompleteOutcomes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DotsBoxesEngine engine = new DotsBoxesEngine(List.of(first, second));

        int guard = 0;
        while (!engine.terminal() && guard++ < DotsBoxesEngine.TOTAL_EDGES) {
            DotsBoxesSnapshot snapshot = engine.snapshot();
            EdgeMove edge = snapshot.legalMoves().getFirst();
            draw(engine, snapshot.currentTurnPlayerId(), edge.orientation(), edge.row(), edge.column());
        }

        DotsBoxesSnapshot terminal = engine.snapshot();
        assertThat(engine.terminal()).isTrue();
        assertThat(terminal.sequence()).isEqualTo(DotsBoxesEngine.TOTAL_EDGES);
        assertThat(terminal.legalMoves()).isEmpty();
        assertThat(terminal.scores()).satisfies(scores -> assertThat(scores.get(0) + scores.get(1)).isEqualTo(16));
        assertThat(engine.outcomes()).hasSize(2);
        assertThat(engine.outcomes()).extracting(outcome -> outcome.score())
                .containsExactly(terminal.scores().get(0).longValue(), terminal.scores().get(1).longValue());
    }

    @Test
    void rejectsInvalidPlayersWrongTurnAndUnsupportedPayloadFields() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThatThrownBy(() -> new DotsBoxesEngine(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
        DotsBoxesEngine engine = new DotsBoxesEngine(List.of(first, second));
        assertThatThrownBy(() -> draw(engine, second, "H", 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");
        assertThatThrownBy(() -> engine.input(first,
                new GameInput("DRAW_HORIZONTAL", 0, 0, null, "x"), NOW))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("coordinates only");
    }

    private EngineUpdate draw(DotsBoxesEngine engine, UUID player, String orientation, int row, int column) {
        String action = "H".equals(orientation) ? "DRAW_HORIZONTAL" : "DRAW_VERTICAL";
        return engine.input(player, new GameInput(action, row, column, null), NOW);
    }
}
