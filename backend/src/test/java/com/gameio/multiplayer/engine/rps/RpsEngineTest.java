package com.gameio.multiplayer.engine.rps;

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

class RpsEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void hidesPendingChoiceAndResolvesAFirstToThreeMatch() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RpsEngine engine = new RpsEngine(List.of(first, second));

        RpsSnapshot pending = (RpsSnapshot) choose(engine, first, 0).snapshot();
        assertThat(pending.players().getFirst().submitted()).isTrue();
        assertThat(pending.players().get(1).submitted()).isFalse();
        assertThat(pending.lastRound()).isNull();
        assertThatThrownBy(() -> choose(engine, first, 1))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("already locked");

        choose(engine, second, 2);
        choose(engine, first, 0);
        choose(engine, second, 2);
        choose(engine, first, 0);
        EngineUpdate terminal = choose(engine, second, 2);

        RpsSnapshot snapshot = (RpsSnapshot) terminal.snapshot();
        assertThat(snapshot.sequence()).isEqualTo(6);
        assertThat(snapshot.round()).isEqualTo(3);
        assertThat(snapshot.players().getFirst().wins()).isEqualTo(3);
        assertThat(snapshot.lastRound().firstChoice()).isEqualTo("ROCK");
        assertThat(snapshot.lastRound().secondChoice()).isEqualTo("SCISSORS");
        assertThat(snapshot.winnerId()).isEqualTo(first);
        assertThat(terminal.terminal()).isTrue();
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 3L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 0L));
    }

    @Test
    void restoresASecretPendingChoiceWithoutRevealingIt() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RpsEngine original = new RpsEngine(List.of(first, second));
        choose(original, first, 1);

        RpsEngine restored = new RpsEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        RpsSnapshot resolved = (RpsSnapshot) choose(restored, second, 0).snapshot();
        assertThat(resolved.lastRound().winnerId()).isEqualTo(first);
        assertThat(resolved.round()).isEqualTo(2);
    }

    private EngineUpdate choose(RpsEngine engine, UUID player, int choice) {
        return engine.input(player, new GameInput("SELECT_MOVE", null, choice, null), NOW);
    }
}
