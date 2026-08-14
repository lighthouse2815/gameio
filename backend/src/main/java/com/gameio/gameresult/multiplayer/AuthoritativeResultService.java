package com.gameio.gameresult.multiplayer;

import com.gameio.achievement.AchievementProgress;
import com.gameio.achievement.AchievementService;
import com.gameio.achievement.UnlockedAchievementResponse;
import com.gameio.competition.CompetitiveRatingService;
import com.gameio.competition.RatingChange;
import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameResult;
import com.gameio.gameresult.GameResultRepository;
import com.gameio.gameresult.GameResultType;
import com.gameio.leaderboard.LeaderboardCacheInvalidator;
import com.gameio.user.LevelService;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthoritativeResultService {
    private final GameResultRepository results;
    private final GameRepository games;
    private final UserRepository users;
    private final LevelService levelService;
    private final AchievementService achievementService;
    private final LeaderboardCacheInvalidator leaderboardCacheInvalidator;
    private final CompetitiveRatingService ratingService;
    private final Clock clock;

    public AuthoritativeResultService(
            GameResultRepository results,
            GameRepository games,
            UserRepository users,
            LevelService levelService,
            AchievementService achievementService,
            LeaderboardCacheInvalidator leaderboardCacheInvalidator,
            CompetitiveRatingService ratingService,
            Clock clock) {
        this.results = results;
        this.games = games;
        this.users = users;
        this.levelService = levelService;
        this.achievementService = achievementService;
        this.leaderboardCacheInvalidator = leaderboardCacheInvalidator;
        this.ratingService = ratingService;
        this.clock = clock;
    }

    @Transactional
    public List<PlayerProgression> record(AuthoritativeMatchResult match) {
        if (results.existsByMatchId(match.matchId())) {
            return List.of();
        }
        Game game = games.findById(match.gameId()).filter(Game::isEnabled)
                .orElseThrow(GameNotFoundException::new);
        Instant playedAt = Instant.now(clock);
        List<PlayerProgression> progression = new ArrayList<>();
        for (AuthoritativePlayerOutcome outcome : match.outcomes()) {
            UserAccount user = users.findById(outcome.userId()).orElseThrow(UserNotFoundException::new);
            results.save(GameResult.authoritativeMatch(match.matchId(), game, user, outcome.score(),
                    outcome.result(), match.durationSeconds(), playedAt));
            long experienceBefore = user.getExp();
            levelService.grant(user, outcome.result() == GameResultType.WIN ? 30 : 10);

            long completedGames = results.countByPlayerId(user.getId());
            long wins = results.countByPlayerIdAndResult(user.getId(), GameResultType.WIN);
            long snakeBestScore = results.maximumScore(user.getId(), "snake");
            long ticTacToeWins = results.countWins(user.getId(), "tic-tac-toe");
            List<UnlockedAchievementResponse> unlocked = achievementService.evaluate(user,
                    new AchievementProgress(completedGames, wins, snakeBestScore, ticTacToeWins));
            progression.add(new PlayerProgression(user.getId(), outcome.result(), outcome.score(),
                    user.getExp() - experienceBefore, user.getLevel(), 0, 0, 0, unlocked));
        }
        Map<UUID, RatingChange> ratingChanges = ratingService.update(match);
        progression = progression.stream().map(player -> {
            RatingChange change = ratingChanges.get(player.userId());
            return change == null ? player : new PlayerProgression(player.userId(), player.result(), player.score(),
                    player.expAwarded(), player.level(), change.before(), change.after(), change.delta(),
                    player.unlockedAchievements());
        }).toList();
        leaderboardCacheInvalidator.afterCommit(game.getId());
        return List.copyOf(progression);
    }
}
