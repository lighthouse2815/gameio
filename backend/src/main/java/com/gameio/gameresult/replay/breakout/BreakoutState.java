package com.gameio.gameresult.replay.breakout;

import java.util.List;

public record BreakoutState(
        int width,
        int height,
        int paddleX,
        int ballX,
        int ballY,
        int velocityX,
        int velocityY,
        List<Boolean> bricks,
        long score,
        int lives,
        int tick,
        String status) {
    public BreakoutState {
        bricks = List.copyOf(bricks);
    }
}
