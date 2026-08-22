package com.gameio.multiplayer.engine.ultimatettt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UltimateTttEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void enforcesTurnAndRequiredSubBoardFromThePreviousCell() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UltimateTttEngine engine = new UltimateTttEngine(List.of(first, second));

        assertThatThrownBy(() -> place(engine, second, 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");

        UltimateTttSnapshot afterFirst = (UltimateTttSnapshot) place(engine, first, 0, 0).snapshot();
        assertThat(afterFirst.forcedBoardRow()).isZero();
        assertThat(afterFirst.forcedBoardColumn()).isZero();
        assertThat(afterFirst.currentTurnPlayerId()).isEqualTo(second);
        assertThat(afterFirst.lastMoveRow()).isZero();
        assertThat(afterFirst.lastMoveColumn()).isZero();
        assertThat(afterFirst.legalMoves()).allMatch(move -> move.row() < 3 && move.column() < 3);

        assertThatThrownBy(() -> place(engine, second, 3, 3))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("required sub-board");
        assertThatThrownBy(() -> engine.input(second,
                new GameInput("PLACE_MARK", 0, 1, 7L), NOW))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("coordinates only");
    }

    @Test
    void restoresExactBoardForcedTargetAndTurnBeforeContinuing() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UltimateTttEngine original = new UltimateTttEngine(List.of(first, second));
        place(original, first, 4, 4);
        place(original, second, 3, 3);

        UltimateTttEngine restored = new UltimateTttEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        UltimateTttSnapshot next = (UltimateTttSnapshot) place(restored, first, 0, 0).snapshot();
        assertThat(next.sequence()).isEqualTo(3);
        assertThat(next.currentTurnPlayerId()).isEqualTo(second);
        assertThat(next.forcedBoardRow()).isZero();
        assertThat(next.forcedBoardColumn()).isZero();
    }

    @Test
    void deterministicLegalPlayReachesOneAuthoritativeTerminalOutcome() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UltimateTttEngine engine = new UltimateTttEngine(List.of(first, second));

        int guard = 0;
        while (!engine.terminal() && guard++ < 81) {
            UltimateTttSnapshot snapshot = engine.snapshot();
            UltimateTttMove move = snapshot.legalMoves().getFirst();
            place(engine, snapshot.currentTurnPlayerId(), move.row(), move.column());
        }

        UltimateTttSnapshot terminal = engine.snapshot();
        assertThat(engine.terminal()).isTrue();
        assertThat(terminal.legalMoves()).isEmpty();
        assertThat(terminal.currentTurnPlayerId()).isNull();
        assertThat(terminal.winnerId() != null || terminal.draw()).isTrue();
        assertThat(engine.outcomes()).hasSize(2);
        assertThat(engine.outcomes()).extracting(outcome -> outcome.userId())
                .containsExactly(first, second);
    }

    @Test
    void rejectsCheckpointWhoseForcedBoardDoesNotMatchTheLastMove() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UltimateTttEngine original = new UltimateTttEngine(List.of(first, second));
        place(original, first, 0, 0);
        UltimateTttCheckpoint checkpoint = original.checkpoint();
        UltimateTttCheckpoint corrupt = new UltimateTttCheckpoint(checkpoint.version(), checkpoint.sequence(),
                checkpoint.board(), checkpoint.currentPlayer(), checkpoint.moves(), 1, checkpoint.winnerId(),
                checkpoint.draw(), checkpoint.lastMoveRow(), checkpoint.lastMoveColumn());

        assertThatThrownBy(() -> new UltimateTttEngine(List.of(first, second), corrupt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    private EngineUpdate place(UltimateTttEngine engine, UUID player, int row, int column) {
        return engine.input(player, new GameInput("PLACE_MARK", row, column, null), NOW);
    }
}
