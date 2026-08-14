package com.gameio.dailychallenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.achievement.AchievementService;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameSession;
import com.gameio.gameresult.GameSessionRepository;
import com.gameio.gameresult.replay.ReplayVerifierRegistry;
import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyChallengeSelectorTest {
    @Test
    void rotatesAcrossAllSoloEnginesAndKeepsSeedStable() {
        DailyChallengeSelector selector = new DailyChallengeSelector();
        LocalDate start = LocalDate.of(2026, 8, 10);
        List<String> rotation = java.util.stream.IntStream.range(0, 6)
                .mapToObj(offset -> selector.gameSlug(start.plusDays(offset)))
                .toList();

        assertThat(rotation).containsExactlyInAnyOrder(
                "2048", "snake", "flappy-bird", "breakout", "minesweeper", "memory-match");
        String slug = selector.gameSlug(start);
        assertThat(selector.seed(start, slug)).isPositive();
        assertThat(selector.seed(start, slug)).isEqualTo(selector.seed(start, slug));
        assertThat(selector.seed(start.plusDays(1), selector.gameSlug(start.plusDays(1))))
                .isNotEqualTo(selector.seed(start, slug));
    }

    @Test
    void calculatesCurrentAndLongestConsecutiveStreaks() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        List<LocalDate> dates = List.of(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(5),
                today.minusDays(6));

        assertThat(DailyChallengeService.currentStreak(dates, today)).isEqualTo(3);
        assertThat(DailyChallengeService.longestStreak(dates)).isEqualTo(3);
        assertThat(DailyChallengeService.currentStreak(
                List.of(today.minusDays(1), today.minusDays(2)), today)).isEqualTo(2);
        assertThat(DailyChallengeService.currentStreak(
                List.of(today.minusDays(3)), today)).isZero();
    }

    @Test
    void dailyCompletionEvaluatesStreakAchievementsOnlyForChallengeSessions() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        DailyChallengeQueryRepository queries = mock(DailyChallengeQueryRepository.class);
        AchievementService achievements = mock(AchievementService.class);
        UserAccount user = mock(UserAccount.class);
        GameSession session = mock(GameSession.class);
        when(user.getId()).thenReturn(java.util.UUID.randomUUID());
        when(session.getChallengeDate()).thenReturn(today);
        when(queries.completedDates(user.getId()))
                .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2)));
        when(queries.distinctSoloGames(user.getId())).thenReturn(3L);
        DailyChallengeService service = new DailyChallengeService(
                new DailyChallengeSelector(), queries, mock(GameSessionRepository.class),
                mock(GameRepository.class), mock(UserRepository.class), mock(ReplayVerifierRegistry.class),
                achievements, Clock.fixed(Instant.parse("2026-08-14T08:00:00Z"), ZoneOffset.UTC));

        service.evaluateCompletion(session, user);

        verify(achievements).evaluateDaily(user, 3, 3, 3);
    }
}
