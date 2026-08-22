package com.gameio.multiplayer.engine.mancala;

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

class MancalaEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void sowsIntoTheOwnStoreAndKeepsTheTurn() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MancalaEngine engine = new MancalaEngine(List.of(first, second));

        MancalaSnapshot snapshot = (MancalaSnapshot) sow(engine, first, 2).snapshot();

        assertThat(snapshot.pits()).containsExactly(
                4, 4, 0, 5, 5, 5, 1,
                4, 4, 4, 4, 4, 4, 0);
        assertThat(snapshot.scores()).containsExactly(1, 0);
        assertThat(snapshot.lastPit()).isEqualTo(6);
        assertThat(snapshot.currentTurnPlayerId()).isEqualTo(first);
        assertThatThrownBy(() -> sow(engine, second, 0))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("turn");
    }

    @Test
    void capturesTheOppositePitAndProducesAuthoritativeTerminalScores() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<Integer> ring = emptyRing();
        ring.set(2, 1);
        ring.set(9, 5);
        ring.set(6, 20);
        ring.set(13, 22);
        MancalaCheckpoint checkpoint = new MancalaCheckpoint(MancalaCheckpoint.CURRENT_VERSION,
                10, ring, 0, 0, null, false);
        MancalaEngine engine = new MancalaEngine(List.of(first, second), checkpoint);

        EngineUpdate update = sow(engine, first, 2);
        MancalaSnapshot terminal = (MancalaSnapshot) update.snapshot();

        assertThat(update.terminal()).isTrue();
        assertThat(terminal.scores()).containsExactly(26, 22);
        assertThat(terminal.pits().subList(0, 6)).containsOnly(0);
        assertThat(terminal.pits().subList(7, 13)).containsOnly(0);
        assertThat(terminal.winnerId()).isEqualTo(first);
        assertThat(update.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 26L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 22L));
    }

    @Test
    void restoresTheExactRingTurnAndSequenceBeforeContinuing() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MancalaEngine original = new MancalaEngine(List.of(first, second));
        sow(original, first, 2);
        sow(original, first, 0);

        MancalaEngine restored = new MancalaEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        MancalaSnapshot next = (MancalaSnapshot) sow(restored, second, 2).snapshot();
        assertThat(next.sequence()).isEqualTo(3);
        assertThat(next.currentTurnPlayerId()).isEqualTo(second);
        assertThat(next.lastPit()).isEqualTo(13);
    }

    @Test
    void rejectsInvalidPlayersEmptyPitsAndUnsupportedFields() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThatThrownBy(() -> new MancalaEngine(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
        MancalaEngine engine = new MancalaEngine(List.of(first, second));
        sow(engine, first, 2);
        assertThatThrownBy(() -> sow(engine, first, 2))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> engine.input(first,
                new GameInput("SOW_PIT", 0, 1, null), NOW))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("relative pit");
    }

    private List<Integer> emptyRing() {
        return new ArrayList<>(java.util.Collections.nCopies(MancalaEngine.RING_SIZE, 0));
    }

    private EngineUpdate sow(MancalaEngine engine, UUID player, int pit) {
        return engine.input(player, new GameInput("SOW_PIT", null, pit, null), NOW);
    }
}
