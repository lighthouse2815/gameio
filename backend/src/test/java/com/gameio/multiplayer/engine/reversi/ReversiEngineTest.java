package com.gameio.multiplayer.engine.reversi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReversiEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void exposesLegalMovesAndFlipsCapturedDiscs() {
        UUID black = UUID.randomUUID();
        UUID white = UUID.randomUUID();
        ReversiEngine engine = new ReversiEngine(List.of(black, white));
        ReversiSnapshot initial = engine.snapshot();

        assertThat(initial.legalMoves()).containsExactlyInAnyOrder(
                new ReversiMove(2, 3), new ReversiMove(3, 2),
                new ReversiMove(4, 5), new ReversiMove(5, 4));
        assertThatThrownBy(() -> place(engine, white, 2, 3))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");
        assertThatThrownBy(() -> place(engine, black, 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("captures no");

        ReversiSnapshot next = (ReversiSnapshot) place(engine, black, 2, 3).snapshot();

        assertThat(next.board().get(2).get(3)).isEqualTo("B");
        assertThat(next.board().get(3).get(3)).isEqualTo("B");
        assertThat(next.blackCount()).isEqualTo(4);
        assertThat(next.whiteCount()).isEqualTo(1);
        assertThat(next.currentTurnPlayerId()).isEqualTo(white);
    }

    @Test
    void restoresPendingPositionAndCanPlayToAuthoritativeTerminalState() {
        UUID black = UUID.randomUUID();
        UUID white = UUID.randomUUID();
        ReversiEngine original = new ReversiEngine(List.of(black, white));
        place(original, black, 2, 3);
        ReversiEngine engine = new ReversiEngine(List.of(black, white), original.checkpoint());
        assertThat(engine.snapshot()).isEqualTo(original.snapshot());

        int guard = 0;
        while (!engine.terminal() && guard++ < 60) {
            ReversiSnapshot snapshot = engine.snapshot();
            ReversiMove move = snapshot.legalMoves().getFirst();
            place(engine, snapshot.currentTurnPlayerId(), move.row(), move.column());
        }

        ReversiSnapshot terminal = engine.snapshot();
        assertThat(engine.terminal()).isTrue();
        assertThat(terminal.legalMoves()).isEmpty();
        assertThat(terminal.blackCount() + terminal.whiteCount()).isBetween(5, 64);
        assertThat(engine.outcomes()).hasSize(2);
        assertThat(engine.outcomes().stream().mapToLong(outcome -> outcome.score()).sum())
                .isEqualTo(terminal.blackCount() + terminal.whiteCount());
    }

    private EngineUpdate place(ReversiEngine engine, UUID player, int row, int column) {
        return engine.input(player, new GameInput("PLACE_DISC", row, column, null), NOW);
    }
}
