package com.gameio.gameresult.replay.snake;

final class SnakeRandom {
    private static final long UINT32_MASK = 0xffff_ffffL;
    private static final long ZERO_SEED_FALLBACK = 0x6d2b79f5L;
    private long state;

    SnakeRandom(long seed) {
        state = seed & UINT32_MASK;
        if (state == 0) {
            state = ZERO_SEED_FALLBACK;
        }
    }

    int nextIndex(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long value = state;
        value ^= (value << 13) & UINT32_MASK;
        value ^= value >>> 17;
        value ^= (value << 5) & UINT32_MASK;
        state = value & UINT32_MASK;
        return (int) ((state * bound) >>> 32);
    }
}
