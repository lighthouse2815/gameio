package com.gameio.dailychallenge;

import com.gameio.achievement.AchievementService;
import com.gameio.achievement.UnlockedAchievementResponse;
import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameSession;
import com.gameio.gameresult.GameSessionRepository;
import com.gameio.gameresult.GameSessionResponse;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.ReplayVerifierRegistry;
import com.gameio.leaderboard.LeaderboardResponse;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyChallengeService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final DailyChallengeSelector selector;
    private final DailyChallengeQueryRepository queries;
    private final GameSessionRepository sessions;
    private final GameRepository games;
    private final UserRepository users;
    private final ReplayVerifierRegistry verifiers;
    private final AchievementService achievements;
    private final Clock clock;

    public DailyChallengeService(
            DailyChallengeSelector selector,
            DailyChallengeQueryRepository queries,
            GameSessionRepository sessions,
            GameRepository games,
            UserRepository users,
            ReplayVerifierRegistry verifiers,
            AchievementService achievements,
            Clock clock) {
        this.selector = selector;
        this.queries = queries;
        this.sessions = sessions;
        this.games = games;
        this.users = users;
        this.verifiers = verifiers;
        this.achievements = achievements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyChallengeResponse today() {
        return challenge(todayDate());
    }

    @Transactional
    public GameSessionResponse startToday(UUID userId) {
        LocalDate date = todayDate();
        Game game = game(date);
        UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        GameReplayVerifier verifier = verifiers.require(game.getSlug());
        long seed = selector.seed(date, game.getSlug());
        Instant now = Instant.now(clock);
        Instant dayEnd = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant expiresAt = now.plus(SESSION_TTL).isBefore(dayEnd) ? now.plus(SESSION_TTL) : dayEnd;
        GameSession session = GameSession.startDailyChallenge(game, user, seed, date, now, expiresAt);
        sessions.save(session);
        return new GameSessionResponse(session.getId(), game.getSlug(), seed, verifier.initialState(seed),
                expiresAt, date);
    }

    @Transactional(readOnly = true)
    public DailyChallengeProgressResponse progress(UUID userId) {
        LocalDate today = todayDate();
        List<LocalDate> dates = queries.completedDates(userId);
        long current = currentStreak(dates, today);
        long longest = longestStreak(dates);
        long best = queries.bestScore(userId, today);
        return new DailyChallengeProgressResponse(today, dates.contains(today), best, dates.size(), current,
                longest, queries.distinctSoloGames(userId));
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(LocalDate date, int page, int size) {
        challenge(date);
        return queries.leaderboard(date, page, size);
    }

    public List<UnlockedAchievementResponse> evaluateCompletion(GameSession session, UserAccount user) {
        if (session.getChallengeDate() == null) return List.of();
        LocalDate today = todayDate();
        List<LocalDate> dates = queries.completedDates(user.getId());
        return achievements.evaluateDaily(user, dates.size(), currentStreak(dates, today),
                queries.distinctSoloGames(user.getId()));
    }

    private DailyChallengeResponse challenge(LocalDate date) {
        Game game = game(date);
        return new DailyChallengeResponse(date, game.getId(), game.getSlug(), game.getName(), game.getDescription(),
                date.atStartOfDay(BUSINESS_ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    private Game game(LocalDate date) {
        return games.findBySlugAndEnabledTrue(selector.gameSlug(date)).orElseThrow(GameNotFoundException::new);
    }

    private LocalDate todayDate() {
        return Instant.now(clock).atZone(BUSINESS_ZONE).toLocalDate();
    }

    static long currentStreak(List<LocalDate> descendingDates, LocalDate today) {
        if (descendingDates.isEmpty()) return 0;
        LocalDate expected = descendingDates.getFirst();
        if (!expected.equals(today) && !expected.equals(today.minusDays(1))) return 0;
        long streak = 0;
        for (LocalDate date : descendingDates) {
            if (!date.equals(expected)) break;
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    static long longestStreak(List<LocalDate> descendingDates) {
        if (descendingDates.isEmpty()) return 0;
        long longest = 1;
        long current = 1;
        for (int index = 1; index < descendingDates.size(); index++) {
            if (descendingDates.get(index).equals(descendingDates.get(index - 1).minusDays(1))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
