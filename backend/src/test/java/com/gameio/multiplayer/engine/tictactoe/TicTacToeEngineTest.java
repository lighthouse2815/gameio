package com.gameio.multiplayer.engine.tictactoe;

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

class TicTacToeEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void validatesTurnsAndProducesAuthoritativeWinOutcomes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TicTacToeEngine engine = new TicTacToeEngine(List.of(first, second));

        assertThatThrownBy(() -> place(engine, second, 0, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");

        place(engine, first, 0, 0);
        place(engine, second, 1, 0);
        place(engine, first, 0, 1);
        place(engine, second, 1, 1);
        EngineUpdate terminal = place(engine, first, 0, 2);

        TicTacToeSnapshot snapshot = (TicTacToeSnapshot) terminal.snapshot();
        assertThat(terminal.terminal()).isTrue();
        assertThat(snapshot.winnerId()).isEqualTo(first);
        assertThat(snapshot.sequence()).isEqualTo(5);
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 1L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 0L));
        assertThatThrownBy(() -> place(engine, second, 2, 2))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("already over");
    }

    private EngineUpdate place(TicTacToeEngine engine, UUID userId, int row, int column) {
        return engine.input(userId, new GameInput("PLACE_PIECE", row, column, null), NOW);
    }
}
