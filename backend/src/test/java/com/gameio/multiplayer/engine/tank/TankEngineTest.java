package com.gameio.multiplayer.engine.tank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TankEngineTest {
    private static final Instant START = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void acceptsOnlyMonotonicPlayerInputAndKeepsTankInsideAuthoritativeBounds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TankEngine engine = new TankEngine(List.of(first, second));

        TankSnapshot afterInput = (TankSnapshot) engine.input(first,
                new GameInput("MOVE_LEFT", null, null, 1L), START).snapshot();
        assertThat(afterInput.sequence()).isEqualTo(1);
        assertThat(afterInput.tanks().getFirst().lastInputSequence()).isEqualTo(1);

        for (int tick = 1; tick <= 30; tick++) {
            engine.tick(START.plus(tick * 100L, ChronoUnit.MILLIS));
        }
        TankView bounded = engine.snapshot().tanks().stream()
                .filter(tank -> tank.userId().equals(first))
                .findFirst().orElseThrow();
        assertThat(bounded.x()).isEqualTo(2.0);
        assertThat(bounded.y()).isBetween(2.0, TankEngine.HEIGHT - 2.0);

        assertThatThrownBy(() -> engine.input(first,
                new GameInput("MOVE_RIGHT", null, null, 1L), START.plusSeconds(4)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("increase monotonically");
        assertThatThrownBy(() -> engine.input(UUID.randomUUID(),
                new GameInput("SHOOT", null, null, 2L), START.plusSeconds(4)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("not part");
    }

    @Test
    void shootIsServerOwnedAndCooldownIsEnforced() {
        UUID first = UUID.randomUUID();
        TankEngine engine = new TankEngine(List.of(first, UUID.randomUUID()));

        TankSnapshot shot = (TankSnapshot) engine.input(first,
                new GameInput("SHOOT", null, null, 1L), START).snapshot();
        assertThat(shot.bullets()).hasSize(1);
        assertThat(shot.bullets().getFirst().ownerId()).isEqualTo(first);

        assertThatThrownBy(() -> engine.input(first,
                new GameInput("SHOOT", null, null, 2L), START.plusMillis(499)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("cooling down");
    }

    @Test
    void restoresMovementBulletsCooldownAndTickClockExactly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TankEngine original = new TankEngine(List.of(first, second));
        original.input(first, new GameInput("MOVE_RIGHT", null, null, 1L), START);
        original.input(first, new GameInput("SHOOT", null, null, 2L), START.plusMillis(10));
        original.tick(START.plusMillis(100));

        TankEngine restored = new TankEngine(List.of(first, second), original.checkpoint());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThatThrownBy(() -> restored.input(first,
                new GameInput("SHOOT", null, null, 3L), START.plusMillis(400)))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("cooling down");
        assertThat(restored.tick(START.plusMillis(150)).snapshot())
                .isEqualTo(original.tick(START.plusMillis(150)).snapshot());
    }
}
