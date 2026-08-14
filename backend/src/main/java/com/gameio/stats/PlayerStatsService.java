package com.gameio.stats;

import com.gameio.achievement.AchievementService;
import com.gameio.gameresult.GameResult;
import com.gameio.gameresult.GameResultRepository;
import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.GameStatsProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PlayerStatsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int ACTIVITY_DAYS = 30;

    private final GameResultRepository results;
    private final AchievementService achievements;
    private final Clock clock;

    PlayerStatsService(GameResultRepository results, AchievementService achievements, Clock clock) {
        this.results = results;
        this.achievements = achievements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PlayerStatsResponse stats(UUID userId) {
        List<GameStatsProjection> rows = results.statisticsByGame(userId);
        List<GameStatsResponse> perGame = rows.stream().map(GameStatsResponse::from).toList();
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Instant activityStart = today.minusDays(ACTIVITY_DAYS - 1L).atStartOfDay(BUSINESS_ZONE).toInstant();
        List<GameResult> recentResults = results
                .findByPlayerIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(userId, activityStart);
        List<DailyActivityResponse> activity = activity(today, recentResults);
        StatsSummaryResponse summary = summary(rows, activity);
        ScoreTrendResponse trend = trend(today, recentResults);
        long unlocked = achievements.listForUser(userId).size();
        long achievementTotal = achievements.listAll().size();
        AchievementStatsResponse achievementStats = new AchievementStatsResponse(unlocked, achievementTotal,
                achievementTotal == 0 ? 0 : unlocked * 100.0 / achievementTotal);
        String mostPlayed = perGame.isEmpty() ? null : perGame.getFirst().gameSlug();
        String strongestMultiplayer = perGame.stream()
                .filter(game -> game.wins() + game.losses() + game.draws() >= 3)
                .max(Comparator.comparingDouble(GameStatsResponse::winRate)
                        .thenComparingLong(GameStatsResponse::gamesPlayed))
                .map(GameStatsResponse::gameSlug)
                .orElse(null);
        return new PlayerStatsResponse(summary, perGame, activity, trend, achievementStats,
                mostPlayed, strongestMultiplayer);
    }

    private StatsSummaryResponse summary(
            List<GameStatsProjection> rows, List<DailyActivityResponse> activity) {
        long gamesPlayed = rows.stream().mapToLong(GameStatsProjection::getGamesPlayed).sum();
        long wins = rows.stream().mapToLong(GameStatsProjection::getWins).sum();
        long losses = rows.stream().mapToLong(GameStatsProjection::getLosses).sum();
        long draws = rows.stream().mapToLong(GameStatsProjection::getDraws).sum();
        long completed = rows.stream().mapToLong(GameStatsProjection::getCompleted).sum();
        long totalScore = rows.stream().mapToLong(GameStatsProjection::getTotalScore).sum();
        long bestScore = rows.stream().mapToLong(GameStatsProjection::getBestScore).max().orElse(0);
        long totalDuration = rows.stream().mapToLong(GameStatsProjection::getTotalDurationSeconds).sum();
        double averageScore = gamesPlayed == 0 ? 0 : totalScore * 1.0 / gamesPlayed;
        long competitiveGames = wins + losses + draws;
        double winRate = competitiveGames == 0 ? 0 : wins * 100.0 / competitiveGames;
        List<LocalDate> activeDates = activity.stream()
                .filter(day -> day.gamesPlayed() > 0)
                .map(DailyActivityResponse::date)
                .toList();
        return new StatsSummaryResponse(gamesPlayed, wins, losses, draws, completed, totalScore, bestScore,
                averageScore, totalDuration, winRate, activeDates.size(), playStreak(activeDates));
    }

    private List<DailyActivityResponse> activity(LocalDate today, List<GameResult> recentResults) {
        Map<LocalDate, MutableActivity> grouped = new LinkedHashMap<>();
        for (int offset = ACTIVITY_DAYS - 1; offset >= 0; offset--) {
            grouped.put(today.minusDays(offset), new MutableActivity());
        }
        recentResults.forEach(result -> {
            LocalDate date = result.getPlayedAt().atZone(BUSINESS_ZONE).toLocalDate();
            MutableActivity day = grouped.get(date);
            if (day != null) day.add(result);
        });
        List<DailyActivityResponse> response = new ArrayList<>(grouped.size());
        grouped.forEach((date, day) -> response.add(day.response(date)));
        return List.copyOf(response);
    }

    private ScoreTrendResponse trend(LocalDate today, List<GameResult> recentResults) {
        LocalDate recentStart = today.minusDays(6);
        LocalDate previousStart = today.minusDays(13);
        long recentScore = 0;
        long recentCount = 0;
        long previousScore = 0;
        long previousCount = 0;
        for (GameResult result : recentResults) {
            LocalDate date = result.getPlayedAt().atZone(BUSINESS_ZONE).toLocalDate();
            if (!date.isBefore(recentStart)) {
                recentScore += result.getScore();
                recentCount++;
            } else if (!date.isBefore(previousStart)) {
                previousScore += result.getScore();
                previousCount++;
            }
        }
        double recentAverage = recentCount == 0 ? 0 : recentScore * 1.0 / recentCount;
        double previousAverage = previousCount == 0 ? 0 : previousScore * 1.0 / previousCount;
        Double change = previousCount == 0 || previousAverage == 0
                ? null
                : (recentAverage - previousAverage) * 100.0 / previousAverage;
        return new ScoreTrendResponse(recentAverage, previousAverage, change);
    }

    private long playStreak(List<LocalDate> activeDates) {
        if (activeDates.isEmpty()) return 0;
        List<LocalDate> descending = activeDates.stream().sorted(Comparator.reverseOrder()).toList();
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate expected = descending.getFirst().equals(today) ? today : today.minusDays(1);
        long streak = 0;
        for (LocalDate date : descending) {
            if (!date.equals(expected)) break;
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private static final class MutableActivity {
        private long games;
        private long wins;
        private long score;
        private long duration;

        void add(GameResult result) {
            games++;
            if (result.getResult() == GameResultType.WIN) wins++;
            score += result.getScore();
            duration += result.getDurationSeconds();
        }

        DailyActivityResponse response(LocalDate date) {
            return new DailyActivityResponse(date, games, wins, score, duration);
        }
    }
}
