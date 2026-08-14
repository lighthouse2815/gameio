package com.gameio.gameresult.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gameio.achievement.AchievementService;
import com.gameio.competition.CompetitiveRatingService;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameResultRepository;
import com.gameio.gameresult.GameResultType;
import com.gameio.leaderboard.LeaderboardCacheInvalidator;
import com.gameio.user.LevelService;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthoritativeResultServiceTest {
    @Test
    void repeatedTerminalDeliveryDoesNotAwardProgressionTwice() {
        GameResultRepository results = mock(GameResultRepository.class);
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        LevelService levels = mock(LevelService.class);
        AchievementService achievements = mock(AchievementService.class);
        UUID matchId = UUID.randomUUID();
        AuthoritativeMatchResult match = new AuthoritativeMatchResult(matchId, UUID.randomUUID(), 30,
                List.of(new AuthoritativePlayerOutcome(UUID.randomUUID(), GameResultType.WIN, 1),
                        new AuthoritativePlayerOutcome(UUID.randomUUID(), GameResultType.LOSS, 0)));
        when(results.existsByMatchId(matchId)).thenReturn(true);
        AuthoritativeResultService service = new AuthoritativeResultService(results, games, users, levels,
                achievements, mock(LeaderboardCacheInvalidator.class), mock(CompetitiveRatingService.class),
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.record(match)).isEmpty();

        verify(results).existsByMatchId(matchId);
        verifyNoMoreInteractions(results);
        verifyNoInteractions(games, users, levels, achievements);
    }
}
