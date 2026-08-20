package com.gameio.multiplayer.engine.connectfour;

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

class ConnectFourEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void appliesGravityAndProducesAuthoritativeHorizontalWin() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ConnectFourEngine engine = new ConnectFourEngine(List.of(first, second));

        assertThatThrownBy(() -> drop(engine, second, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");

        drop(engine, first, 0);
        drop(engine, second, 0);
        drop(engine, first, 1);
        drop(engine, second, 1);
        drop(engine, first, 2);
        drop(engine, second, 2);
        EngineUpdate terminal = drop(engine, first, 3);

        ConnectFourSnapshot snapshot = (ConnectFourSnapshot) terminal.snapshot();
        assertThat(snapshot.board().get(5)).containsExactly("R", "R", "R", "R", "", "", "");
        assertThat(snapshot.board().get(4)).containsExactly("Y", "Y", "Y", "", "", "", "");
        assertThat(snapshot.lastMoveRow()).isEqualTo(5);
        assertThat(snapshot.lastMoveColumn()).isEqualTo(3);
        assertThat(snapshot.winnerId()).isEqualTo(first);
        assertThat(terminal.terminal()).isTrue();
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 1L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 0L));
    }

    @Test
    void restoresBoardTurnAndSequenceFromCheckpoint() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ConnectFourEngine original = new ConnectFourEngine(List.of(first, second));
        drop(original, first, 4);
        drop(original, second, 4);

        ConnectFourEngine restored = new ConnectFourEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        ConnectFourSnapshot next = (ConnectFourSnapshot) drop(restored, first, 4).snapshot();
        assertThat(next.sequence()).isEqualTo(3);
        assertThat(next.lastMoveRow()).isEqualTo(3);
        assertThat(next.currentTurnPlayerId()).isEqualTo(second);
    }

    private EngineUpdate drop(ConnectFourEngine engine, UUID player, int column) {
        return engine.input(player, new GameInput("DROP_DISC", null, column, null), NOW);
    }
}
