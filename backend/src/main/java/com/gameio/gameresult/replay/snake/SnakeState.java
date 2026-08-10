package com.gameio.gameresult.replay.snake;

import java.util.List;

public record SnakeState(
        int width,
        int height,
        List<SnakePoint> body,
        String direction,
        String queuedDirection,
        SnakePoint food,
        long score,
        int tickMs,
        String status
) {
    public SnakeState {
        body = List.copyOf(body);
    }
}
