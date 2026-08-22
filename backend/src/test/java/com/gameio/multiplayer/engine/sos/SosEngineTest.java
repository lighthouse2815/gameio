package com.gameio.multiplayer.engine.sos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SosEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void countsMultipleSosLinesAndAwardsAnExtraTurn() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SosEngine engine = preparedFourLineScore(first, second);

        SosSnapshot snapshot = engine.snapshot();
        assertThat(snapshot.sequence()).isEqualTo(9);
        assertThat(snapshot.players().getFirst().score()).isEqualTo(4);
        assertThat(snapshot.players().get(1).score()).isZero();
        assertThat(snapshot.lastMovePoints()).isEqualTo(4);
        assertThat(snapshot.currentTurnPlayerId()).isEqualTo(first);

        SosSnapshot afterExtraTurn = (SosSnapshot) place(engine, first, "PLACE_S", 0, 0).snapshot();
        assertThat(afterExtraTurn.lastMovePoints()).isZero();
        assertThat(afterExtraTurn.currentTurnPlayerId()).isEqualTo(second);
    }

    @Test
    void fillsBoardThenComparesScoresAndPublishesAuthoritativeOutcomes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SosEngine engine = preparedFourLineScore(first, second);

        while (!engine.terminal()) {
            SosSnapshot snapshot = engine.snapshot();
            int[] empty = firstEmpty(snapshot.board());
            place(engine, snapshot.currentTurnPlayerId(), "PLACE_S", empty[0], empty[1]);
        }

        SosSnapshot terminal = engine.snapshot();
        assertThat(terminal.sequence()).isEqualTo(36);
        assertThat(terminal.currentTurnPlayerId()).isNull();
        assertThat(terminal.winnerId()).isEqualTo(first);
        assertThat(terminal.draw()).isFalse();
        assertThat(terminal.players().getFirst().score()).isEqualTo(4);
        assertThat(terminal.players().get(1).score()).isZero();
        assertThat(engine.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 4L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 0L));
    }

    @Test
    void publishesDrawWhenAFullBoardHasEqualScores() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SosEngine engine = new SosEngine(List.of(first, second));

        while (!engine.terminal()) {
            SosSnapshot snapshot = engine.snapshot();
            int[] empty = firstEmpty(snapshot.board());
            place(engine, snapshot.currentTurnPlayerId(), "PLACE_S", empty[0], empty[1]);
        }

        SosSnapshot terminal = engine.snapshot();
        assertThat(terminal.draw()).isTrue();
        assertThat(terminal.winnerId()).isNull();
        assertThat(terminal.players())
                .extracting(SosPlayerSnapshot::score)
                .containsExactly(0, 0);
        assertThat(engine.outcomes())
                .extracting(outcome -> outcome.result())
                .containsExactly(GameResultType.DRAW, GameResultType.DRAW);
    }

    @Test
    void restoresExactHistoryAndRejectsInvalidMembershipTurnAndCheckpointScore() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> players = List.of(first, second);
        SosEngine original = preparedFourLineScore(first, second);
        JsonMapper mapper = JsonMapper.builder().build();

        SosEngine restored = (SosEngine) new SosEngineFactory().restore(
                players, mapper.valueToTree(original.checkpoint()), mapper);
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThat(restored.checkpoint().version()).isEqualTo(SosCheckpoint.CURRENT_VERSION);

        assertThatThrownBy(() -> place(restored, UUID.randomUUID(), "PLACE_S", 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("belong");
        assertThatThrownBy(() -> place(restored, second, "PLACE_S", 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");
        assertThatThrownBy(() -> place(restored, first, "PLACE_STONE", 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("PLACE_S or PLACE_O");

        SosCheckpoint valid = original.checkpoint();
        List<SosMoveCheckpoint> corruptedHistory = new ArrayList<>(valid.moveHistory());
        SosMoveCheckpoint last = corruptedHistory.getLast();
        corruptedHistory.set(corruptedHistory.size() - 1, new SosMoveCheckpoint(
                last.playerIndex(), last.marker(), last.row(), last.column(), last.points() + 1));
        SosCheckpoint corrupted = new SosCheckpoint(valid.version(), valid.sequence(), valid.board(),
                valid.currentPlayer(), valid.moves(), valid.scores(), valid.winnerId(), valid.draw(),
                corruptedHistory, valid.lastMoveRow(), valid.lastMoveColumn(), valid.lastMovePoints() + 1);

        assertThatThrownBy(() -> new SosEngine(players, corrupted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score history");
    }

    private SosEngine preparedFourLineScore(UUID first, UUID second) {
        SosEngine engine = new SosEngine(List.of(first, second));
        place(engine, first, "PLACE_S", 2, 1);
        place(engine, second, "PLACE_S", 2, 3);
        place(engine, first, "PLACE_S", 1, 2);
        place(engine, second, "PLACE_S", 3, 2);
        place(engine, first, "PLACE_S", 1, 1);
        place(engine, second, "PLACE_S", 3, 3);
        place(engine, first, "PLACE_S", 1, 3);
        place(engine, second, "PLACE_S", 3, 1);
        place(engine, first, "PLACE_O", 2, 2);
        return engine;
    }

    private int[] firstEmpty(List<List<String>> board) {
        for (int row = 0; row < board.size(); row++) {
            for (int column = 0; column < board.get(row).size(); column++) {
                if (board.get(row).get(column).isEmpty()) return new int[]{row, column};
            }
        }
        throw new IllegalStateException("SOS board has no empty cell");
    }

    private EngineUpdate place(SosEngine engine, UUID player, String action, int row, int column) {
        return engine.input(player, new GameInput(action, row, column, null), NOW);
    }
}
