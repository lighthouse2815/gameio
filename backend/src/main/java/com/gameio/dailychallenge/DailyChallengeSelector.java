package com.gameio.dailychallenge;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class DailyChallengeSelector {
    private static final String[] SOLO_GAME_SLUGS = {
            "2048", "snake", "flappy-bird", "breakout", "minesweeper", "memory-match"
    };

    public String gameSlug(LocalDate date) {
        return SOLO_GAME_SLUGS[Math.floorMod(date.toEpochDay(), SOLO_GAME_SLUGS.length)];
    }

    public long seed(LocalDate date, String gameSlug) {
        long mixed = date.toEpochDay() * 0x9E3779B97F4A7C15L;
        mixed ^= Integer.toUnsignedLong(gameSlug.hashCode()) * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        long seed = Integer.toUnsignedLong((int) mixed);
        return seed == 0 ? 1 : seed;
    }
}
