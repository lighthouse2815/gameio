package com.gameio.gameresult.replay.game2048;

final class DeterministicRandom {
    private long state;

    DeterministicRandom(long seed) {
        this.state = seed & 0xffff_ffffL;
        if (this.state == 0) {
            this.state = 0x6d2b79f5L;
        }
    }

    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long value = state;
        value ^= (value << 13) & 0xffff_ffffL;
        value ^= value >>> 17;
        value ^= (value << 5) & 0xffff_ffffL;
        state = value & 0xffff_ffffL;
        return (int) (state % bound);
    }
}
