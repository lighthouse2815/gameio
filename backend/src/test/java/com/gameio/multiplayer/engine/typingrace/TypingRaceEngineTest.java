package com.gameio.multiplayer.engine.typingrace;

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

class TypingRaceEngineTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-18T00:00:00Z");
    private static final Instant STARTS_AT = CREATED_AT.plus(TypingRaceEngine.COUNTDOWN);

    @Test
    void appliesCorrectAndWrongCharactersThenProducesServerTimedOutcomes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TypingRaceEngine engine = engine(first, second, "ab");

        EngineUpdate wrong = type(engine, first, "x", 0, STARTS_AT.plusMillis(500));
        TypingRacePlayerSnapshot afterWrong = player(wrong, first);
        assertThat(afterWrong.progress()).isZero();
        assertThat(afterWrong.errors()).isEqualTo(1);
        assertThat(afterWrong.combo()).isZero();

        type(engine, first, "a", 1, STARTS_AT.plusSeconds(1));
        EngineUpdate terminal = type(engine, first, "b", 2, STARTS_AT.plusSeconds(2));

        TypingRaceSnapshot snapshot = (TypingRaceSnapshot) terminal.snapshot();
        TypingRacePlayerSnapshot winner = snapshot.players().getFirst();
        assertThat(terminal.terminal()).isTrue();
        assertThat(snapshot.winnerId()).isEqualTo(first);
        assertThat(winner.progress()).isEqualTo(2);
        assertThat(winner.correctCharacters()).isEqualTo(2);
        assertThat(winner.errors()).isEqualTo(1);
        assertThat(winner.bestCombo()).isEqualTo(2);
        assertThat(winner.wpm()).isEqualTo(12);
        assertThat(winner.accuracyPercent()).isEqualTo(67);
        assertThat(terminal.outcomes())
                .extracting(outcome -> outcome.userId(), outcome -> outcome.result(), outcome -> outcome.score())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, GameResultType.WIN, 12L),
                        org.assertj.core.groups.Tuple.tuple(second, GameResultType.LOSS, 0L));
        assertThatThrownBy(() -> type(engine, second, "a", 0, STARTS_AT.plusSeconds(3)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("already over");
    }

    @Test
    void enforcesCountdownMembershipActionCharacterAndExactSequence() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TypingRaceEngine engine = engine(first, second, "a b");

        assertThatThrownBy(() -> type(engine, first, "a", 0, STARTS_AT.minusMillis(1)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("countdown");
        assertThatThrownBy(() -> type(engine, UUID.randomUUID(), "a", 0, STARTS_AT))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("not part");
        assertThatThrownBy(() -> engine.input(first,
                new GameInput("MOVE_LEFT", null, null, 0L, "a"), STARTS_AT))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("character input only");
        assertThatThrownBy(() -> engine.input(first,
                new GameInput("TYPE_CHARACTER", null, null, 0L, "ab"), STARTS_AT))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> type(engine, first, "a", 1, STARTS_AT))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("exactly one");

        type(engine, first, "a", 0, STARTS_AT);
        EngineUpdate space = type(engine, first, " ", 1, STARTS_AT.plusSeconds(1));
        assertThat(player(space, first).progress()).isEqualTo(2);
        assertThatThrownBy(() -> type(engine, first, "b", 1, STARTS_AT.plusSeconds(2)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void timeoutRanksProgressThenErrorsAndCanDraw() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TypingRaceEngine ranked = engine(first, second, "abc");
        type(ranked, first, "a", 0, STARTS_AT.plusSeconds(1));
        type(ranked, second, "a", 0, STARTS_AT.plusSeconds(1));
        type(ranked, second, "x", 1, STARTS_AT.plusSeconds(2));

        EngineUpdate timeout = ranked.tick(STARTS_AT.plus(TypingRaceEngine.RACE_DURATION));
        assertThat(((TypingRaceSnapshot) timeout.snapshot()).winnerId()).isEqualTo(first);
        assertThat(timeout.outcomes()).extracting(outcome -> outcome.result())
                .containsExactly(GameResultType.WIN, GameResultType.LOSS);

        TypingRaceEngine tied = engine(first, second, "abc");
        type(tied, first, "a", 0, STARTS_AT.plusSeconds(1));
        type(tied, second, "a", 0, STARTS_AT.plusSeconds(1));
        EngineUpdate draw = tied.tick(STARTS_AT.plus(TypingRaceEngine.RACE_DURATION));
        assertThat(((TypingRaceSnapshot) draw.snapshot()).draw()).isTrue();
        assertThat(draw.outcomes()).extracting(outcome -> outcome.result())
                .containsExactly(GameResultType.DRAW, GameResultType.DRAW);
    }

    @Test
    void checkpointRestoresPassageProgressTimingAndSequence() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TypingRaceEngine original = engine(first, second, "abc");
        type(original, first, "a", 0, STARTS_AT.plusSeconds(1));
        type(original, first, "x", 1, STARTS_AT.plusSeconds(2));

        TypingRaceEngine restored = new TypingRaceEngine(List.of(first, second), original.checkpoint());
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        EngineUpdate continued = type(restored, first, "b", 2, STARTS_AT.plusSeconds(3));
        assertThat(player(continued, first).progress()).isEqualTo(2);
        assertThat(player(continued, first).lastInputSequence()).isEqualTo(2);

        assertThatThrownBy(() -> new TypingRaceEngine(List.of(second, first), original.checkpoint()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("player order");
    }

    @Test
    void requiresExactlyTwoDistinctPlayersAndDoesNotBroadcastIdleTicks() {
        UUID player = UUID.randomUUID();
        assertThatThrownBy(() -> new TypingRaceEngine(List.of(player),
                new TypingPassage("test", "abc"), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two distinct");
        assertThatThrownBy(() -> new TypingRaceEngine(List.of(player, player),
                new TypingPassage("test", "abc"), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two distinct");

        TypingRaceEngine engine = engine(player, UUID.randomUUID(), "abc");
        EngineUpdate idle = engine.tick(STARTS_AT.plusSeconds(10));
        assertThat(idle.changed()).isFalse();
        assertThat(idle.terminal()).isFalse();
    }

    private TypingRaceEngine engine(UUID first, UUID second, String text) {
        return new TypingRaceEngine(List.of(first, second), new TypingPassage("test", text), CREATED_AT);
    }

    private EngineUpdate type(TypingRaceEngine engine, UUID userId, String character, long sequence, Instant now) {
        return engine.input(userId,
                new GameInput("TYPE_CHARACTER", null, null, sequence, character), now);
    }

    private TypingRacePlayerSnapshot player(EngineUpdate update, UUID userId) {
        return ((TypingRaceSnapshot) update.snapshot()).players().stream()
                .filter(player -> player.userId().equals(userId)).findFirst().orElseThrow();
    }
}
