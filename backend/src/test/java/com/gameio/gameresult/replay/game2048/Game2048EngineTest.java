package com.gameio.gameresult.replay.game2048;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Game2048EngineTest {

    @Test
    void sameSeedAndActionsAlwaysProduceSameServerScoreAndBoard() {
        long seed = 3_141_592_653L;
        List<MoveDirection> actions = List.of(
                MoveDirection.LEFT, MoveDirection.UP, MoveDirection.RIGHT, MoveDirection.DOWN,
                MoveDirection.LEFT, MoveDirection.LEFT, MoveDirection.UP, MoveDirection.RIGHT);

        Game2048Engine first = new Game2048Engine(seed);
        Game2048Engine second = new Game2048Engine(seed);
        actions.forEach(direction -> {
            first.move(direction);
            second.move(direction);
        });

        assertThat(first.state()).isEqualTo(second.state());
        assertThat(first.state().score()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void invalidMovesDoNotCreateTilesOrScore() {
        Game2048Engine engine = new Game2048Engine(7);
        Game2048State before = engine.state();
        MoveDirection blockedDirection = before.board().stream().allMatch(row -> row.get(0) == 0)
                ? MoveDirection.RIGHT : MoveDirection.LEFT;

        for (int attempt = 0; attempt < 20; attempt++) {
            if (!engine.move(blockedDirection)) {
                Game2048State stable = engine.state();
                assertThat(engine.move(blockedDirection)).isFalse();
                assertThat(engine.state()).isEqualTo(stable);
                return;
            }
        }
        throw new AssertionError("Expected to encounter a blocked move");
    }
}
