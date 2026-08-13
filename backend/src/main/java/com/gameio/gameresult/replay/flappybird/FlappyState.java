package com.gameio.gameresult.replay.flappybird;

import java.util.List;

public record FlappyState(
        int width,
        int height,
        int birdX,
        int birdY,
        int birdVelocity,
        List<FlappyPipeState> pipes,
        long score,
        int tick,
        int tickMs,
        String status
) {
    public FlappyState {
        pipes = List.copyOf(pipes);
    }
}
