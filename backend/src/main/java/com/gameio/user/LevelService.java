package com.gameio.user;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class LevelService {
    private final Clock clock;

    public LevelService(Clock clock) {
        this.clock = clock;
    }

    public void grant(UserAccount user, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Experience amount must not be negative");
        }
        long resultingExperience = Math.addExact(user.getExp(), amount);
        user.grantExperience(amount, levelFor(resultingExperience), Instant.now(clock));
    }

    public int levelFor(long totalExperience) {
        if (totalExperience < 0) {
            throw new IllegalArgumentException("Experience must not be negative");
        }
        return Math.toIntExact(Math.min(10_000, (long) Math.floor(Math.sqrt(totalExperience / 100.0)) + 1));
    }

    public long experienceRequiredForLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Level must be positive");
        }
        return Math.multiplyExact(100L, Math.multiplyExact((long) level - 1, (long) level - 1));
    }
}
