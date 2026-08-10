package com.gameio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LevelServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
    private final LevelService levelService = new LevelService(clock);

    @Test
    void calculatesQuadraticLevelThresholdsAndGrantsCentrally() {
        UserAccount user = UserAccount.create("PlayerOne", "player@example.com", "hash", Instant.now(clock));

        levelService.grant(user, 99);
        assertThat(user.getLevel()).isEqualTo(1);
        levelService.grant(user, 1);
        assertThat(user.getLevel()).isEqualTo(2);
        assertThat(user.getExp()).isEqualTo(100);
        assertThat(levelService.experienceRequiredForLevel(3)).isEqualTo(400);
    }

    @Test
    void rejectsNegativeExperience() {
        UserAccount user = UserAccount.create("PlayerTwo", "two@example.com", "hash", Instant.now(clock));
        assertThatThrownBy(() -> levelService.grant(user, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
