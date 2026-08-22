package com.gameio.multiplayer.engine.hex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HexEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void enforcesMembershipTurnCoordinatesAndOccupancy() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        HexEngine engine = new HexEngine(List.of(first, second));

        assertThatThrownBy(() -> place(engine, UUID.randomUUID(), 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("belong");
        assertThatThrownBy(() -> place(engine, second, 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");
        assertThatThrownBy(() -> place(engine, first, -1, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("9 by 9");
        assertThatThrownBy(() -> engine.input(first,
                new GameInput("PLACE_PIECE", 0, 0, null), NOW))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("PLACE_STONE");

        HexSnapshot next = (HexSnapshot) place(engine, first, 0, 0).snapshot();
        assertThat(next.board().getFirst().getFirst()).isEqualTo("R");
        assertThat(next.currentTurnPlayerId()).isEqualTo(second);
        assertThatThrownBy(() -> place(engine, second, 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("occupied");
    }

    @Test
    void detectsPlayerZeroTopToBottomConnectionWithSixNeighborTraversal() {
        UUID topBottom = UUID.randomUUID();
        UUID leftRight = UUID.randomUUID();
        HexEngine engine = new HexEngine(List.of(topBottom, leftRight));
        int[] pathColumns = {4, 3, 3, 2, 2, 1, 1, 0, 0};

        for (int row = 0; row < HexEngine.SIZE - 1; row++) {
            place(engine, topBottom, row, pathColumns[row]);
            place(engine, leftRight, row, 8);
        }
        EngineUpdate terminal = place(engine, topBottom, 8, pathColumns[8]);

        HexSnapshot snapshot = (HexSnapshot) terminal.snapshot();
        assertThat(snapshot.sequence()).isEqualTo(17);
        assertThat(snapshot.currentTurnPlayerId()).isNull();
        assertThat(snapshot.winnerId()).isEqualTo(topBottom);
        assertThat(snapshot.lastMoveRow()).isEqualTo(8);
        assertThat(snapshot.lastMoveColumn()).isZero();
        assertThat(terminal.terminal()).isTrue();
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(topBottom, GameResultType.WIN, 1L),
                        org.assertj.core.groups.Tuple.tuple(leftRight, GameResultType.LOSS, 0L));
        assertThatThrownBy(() -> place(engine, leftRight, 8, 8))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("already over");
    }

    @Test
    void detectsPlayerOneLeftToRightConnectionWithoutInventingADraw() {
        UUID topBottom = UUID.randomUUID();
        UUID leftRight = UUID.randomUUID();
        HexEngine engine = new HexEngine(List.of(topBottom, leftRight));

        for (int column = 0; column < HexEngine.SIZE; column++) {
            place(engine, topBottom, 8, column);
            place(engine, leftRight, 0, column);
        }

        assertThat(engine.terminal()).isTrue();
        assertThat(engine.snapshot().winnerId()).isEqualTo(leftRight);
        assertThat(engine.outcomes())
                .extracting(outcome -> outcome.result())
                .containsExactly(GameResultType.LOSS, GameResultType.WIN)
                .doesNotContain(GameResultType.DRAW);
    }

    @Test
    void restoresVersionedCheckpointWithExactBoardTurnAndSequence() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> players = List.of(first, second);
        HexEngine original = new HexEngine(players);
        place(original, first, 0, 4);
        place(original, second, 3, 0);
        place(original, first, 1, 4);

        JsonMapper mapper = JsonMapper.builder().build();
        HexEngine restored = (HexEngine) new HexEngineFactory().restore(
                players, mapper.valueToTree(original.checkpoint()), mapper);

        assertThat(original.checkpoint().version()).isEqualTo(HexCheckpoint.CURRENT_VERSION);
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        HexSnapshot next = (HexSnapshot) place(restored, second, 3, 1).snapshot();
        assertThat(next.sequence()).isEqualTo(4);
        assertThat(next.currentTurnPlayerId()).isEqualTo(first);
    }

    private EngineUpdate place(HexEngine engine, UUID player, int row, int column) {
        return engine.input(player, new GameInput("PLACE_STONE", row, column, null), NOW);
    }
}
