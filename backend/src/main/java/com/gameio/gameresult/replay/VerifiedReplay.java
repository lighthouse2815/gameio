package com.gameio.gameresult.replay;

public record VerifiedReplay(
        long score,
        boolean gameOver,
        int highestValue,
        Object finalState,
        int minimumDurationSeconds
) {
    public VerifiedReplay {
        if (score < 0 || minimumDurationSeconds < 1) {
            throw new IllegalArgumentException("Verified replay metrics must be positive");
        }
    }
}
