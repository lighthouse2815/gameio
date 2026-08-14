package com.gameio.gameresult;

import com.gameio.achievement.AchievementProgress;
import com.gameio.achievement.AchievementService;
import com.gameio.achievement.UnlockedAchievementResponse;
import com.gameio.common.error.ConflictException;
import com.gameio.common.error.InvalidGameActionException;
import com.gameio.common.web.PageResponse;
import com.gameio.dailychallenge.DailyChallengeService;
import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.replay.GameReplayVerifier;
import com.gameio.gameresult.replay.ReplayVerifierRegistry;
import com.gameio.gameresult.replay.VerifiedReplay;
import com.gameio.leaderboard.LeaderboardCacheInvalidator;
import com.gameio.user.LevelService;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameResultService {
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final int MAX_ACTIONS_PER_SECOND = 30;
    private static final int CLOCK_TOLERANCE_SECONDS = 30;

    private final GameSessionRepository sessions;
    private final GameResultRepository results;
    private final GameRepository games;
    private final UserRepository users;
    private final ReplayVerifierRegistry verifierRegistry;
    private final LevelService levelService;
    private final AchievementService achievementService;
    private final LeaderboardCacheInvalidator leaderboardCacheInvalidator;
    private final DailyChallengeService dailyChallenges;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public GameResultService(
            GameSessionRepository sessions,
            GameResultRepository results,
            GameRepository games,
            UserRepository users,
            ReplayVerifierRegistry verifierRegistry,
            LevelService levelService,
            AchievementService achievementService,
            LeaderboardCacheInvalidator leaderboardCacheInvalidator,
            DailyChallengeService dailyChallenges,
            Clock clock) {
        this.sessions = sessions;
        this.results = results;
        this.games = games;
        this.users = users;
        this.verifierRegistry = verifierRegistry;
        this.levelService = levelService;
        this.achievementService = achievementService;
        this.leaderboardCacheInvalidator = leaderboardCacheInvalidator;
        this.dailyChallenges = dailyChallenges;
        this.clock = clock;
    }

    @Transactional
    public GameSessionResponse startSession(UUID userId, StartGameSessionRequest request) {
        Game game = games.findBySlugAndEnabledTrue(request.gameSlug()).orElseThrow(GameNotFoundException::new);
        GameReplayVerifier verifier = verifierRegistry.require(game.getSlug());
        UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        Instant now = Instant.now(clock);
        long seed = Integer.toUnsignedLong(secureRandom.nextInt());
        if (seed == 0) {
            seed = 1;
        }
        GameSession session = GameSession.start(game, user, seed, now, now.plus(SESSION_TTL));
        sessions.save(session);
        return new GameSessionResponse(session.getId(), game.getSlug(), seed, verifier.initialState(seed),
                session.getExpiresAt(), null);
    }

    @Transactional
    public GameResultResponse complete(UUID userId, CompleteGameResultRequest request) {
        GameSession session = sessions.findForUpdate(request.sessionId())
                .orElseThrow(() -> new InvalidGameActionException("Game session was not found"));
        if (!session.getPlayer().getId().equals(userId)) {
            throw new InvalidGameActionException("Game session does not belong to the authenticated user");
        }
        if (session.getStatus() != GameSessionStatus.RUNNING) {
            throw new ConflictException("GAME_SESSION_ALREADY_CLOSED", "Game session is already closed");
        }
        Instant now = Instant.now(clock);
        if (!session.getExpiresAt().isAfter(now)) {
            throw new InvalidGameActionException("Game session has expired");
        }
        GameReplayVerifier verifier = verifierRegistry.require(session.getGame().getSlug());
        VerifiedReplay replay = verifier.verify(session.getRandomSeed(), request.actions());
        if (!replay.gameOver()) {
            throw new InvalidGameActionException("Only a completed game-over replay can create a result");
        }
        validateTiming(session, request, replay, now);

        long completedBefore = results.countByPlayerIdAndGameId(userId, session.getGame().getId());
        long previousBest = results.maximumScore(userId, session.getGame().getSlug());
        session.complete(now);
        GameResult result = results.save(GameResult.verifiedCompletion(
                session, replay.score(), request.durationSeconds(), now));
        UserAccount user = session.getPlayer();
        long experienceBefore = user.getExp();
        levelService.grant(user, 10);

        long completedGames = results.countByPlayerId(userId);
        long wins = results.countByPlayerIdAndResult(userId, GameResultType.WIN);
        long snakeBestScore = results.maximumScore(userId, "snake");
        long ticTacToeWins = results.countWins(userId, "tic-tac-toe");
        List<UnlockedAchievementResponse> unlocked = achievementService.evaluate(user,
                new AchievementProgress(completedGames, wins, snakeBestScore, ticTacToeWins));
        if (session.getChallengeDate() != null) {
            results.flush();
            List<UnlockedAchievementResponse> dailyUnlocked = dailyChallenges.evaluateCompletion(session, user);
            if (!dailyUnlocked.isEmpty()) {
                List<UnlockedAchievementResponse> combined = new java.util.ArrayList<>(unlocked);
                combined.addAll(dailyUnlocked);
                unlocked = List.copyOf(combined);
            }
        }
        leaderboardCacheInvalidator.afterCommit(session.getGame().getId());
        Long previousBestScore = completedBefore == 0 ? null : previousBest;
        boolean personalBest = completedBefore == 0 || replay.score() > previousBest;
        return GameResultResponse.completed(result, user.getExp() - experienceBefore, user.getLevel(),
                previousBestScore, personalBest, unlocked);
    }

    @Transactional(readOnly = true)
    public PageResponse<GameResultResponse> history(UUID userId, int page, int size) {
        return PageResponse.from(results.findByPlayerIdOrderByPlayedAtDesc(userId, PageRequest.of(page, size)),
                GameResultResponse::history);
    }

    private void validateTiming(
            GameSession session, CompleteGameResultRequest request, VerifiedReplay replay, Instant now) {
        int actionRateMinimum = Math.max(1,
                (request.actions().size() + MAX_ACTIONS_PER_SECOND - 1) / MAX_ACTIONS_PER_SECOND);
        int minimumDuration = Math.max(actionRateMinimum, replay.minimumDurationSeconds());
        if (request.durationSeconds() < minimumDuration) {
            throw new InvalidGameActionException("Replay duration is shorter than the verified simulation time");
        }
        long observedSeconds = Math.max(0, Duration.between(session.getStartedAt(), now).toSeconds());
        if (request.durationSeconds() > observedSeconds + CLOCK_TOLERANCE_SECONDS) {
            throw new InvalidGameActionException("Reported duration exceeds the server-observed session duration");
        }
    }
}
