package com.gameio.multiplayer.engine.caro;

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

class CaroEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void detectsFiveInARowAndRejectsOccupiedOrOutOfBoundsMoves() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CaroEngine engine = new CaroEngine(List.of(first, second));

        place(engine, first, 7, 0);
        assertThatThrownBy(() -> place(engine, second, 7, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("occupied");
        place(engine, second, 0, 0);
        place(engine, first, 7, 1);
        place(engine, second, 0, 1);
        place(engine, first, 7, 2);
        place(engine, second, 0, 2);
        place(engine, first, 7, 3);
        place(engine, second, 0, 3);
        EngineUpdate terminal = place(engine, first, 7, 4);

        CaroSnapshot snapshot = (CaroSnapshot) terminal.snapshot();
        assertThat(terminal.terminal()).isTrue();
        assertThat(snapshot.boardSize()).isEqualTo(15);
        assertThat(snapshot.winnerId()).isEqualTo(first);
        assertThat(snapshot.sequence()).isEqualTo(9);
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS));

        CaroEngine boundsEngine = new CaroEngine(List.of(first, second));
        assertThatThrownBy(() -> place(boundsEngine, first, CaroEngine.SIZE, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("outside");
    }

    private EngineUpdate place(CaroEngine engine, UUID userId, int row, int column) {
        return engine.input(userId, new GameInput("PLACE_PIECE", row, column, null), NOW);
    }
}
