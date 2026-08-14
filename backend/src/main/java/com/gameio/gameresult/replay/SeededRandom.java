package com.gameio.gameresult.replay;

public final class SeededRandom {
    private int state;

    public SeededRandom(long seed) {
        state = (int) seed;
        if (state == 0) {
            state = 0x6d2b79f5;
        }
    }

    public int nextIndex(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        int value = state;
        value ^= value << 13;
        value ^= value >>> 17;
        value ^= value << 5;
        state = value;
        long unsigned = Integer.toUnsignedLong(state);
        return (int) ((unsigned * bound) / 4_294_967_296L);
    }
}
