package com.gameio.competition;

import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativeMatchResult;
import com.gameio.gameresult.multiplayer.AuthoritativePlayerOutcome;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitiveRatingService {
    private static final int K_FACTOR = 32;
    private final SeasonService seasons;
    private final SeasonRatingRepository ratings;
    private final UserRepository users;
    private final GameRepository games;
    private final Clock clock;

    CompetitiveRatingService(
            SeasonService seasons,
            SeasonRatingRepository ratings,
            UserRepository users,
            GameRepository games,
            Clock clock) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.users = users;
        this.games = games;
        this.clock = clock;
    }

    @Transactional
    public Map<UUID, RatingChange> update(AuthoritativeMatchResult match) {
        Season season = seasons.current();
        Game game = games.findById(match.gameId()).filter(Game::isEnabled).orElseThrow(GameNotFoundException::new);
        Instant now = Instant.now(clock);
        Map<UUID, SeasonRating> playerRatings = new LinkedHashMap<>();
        match.outcomes().stream().sorted(java.util.Comparator.comparing(AuthoritativePlayerOutcome::userId))
                .forEach(outcome -> playerRatings.put(outcome.userId(), requireRating(
                        season, requireUser(outcome.userId()), game, now)));
        Map<UUID, Integer> ratingsBeforeMatch = playerRatings.values().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        SeasonRating::userId, SeasonRating::rating));
        Map<UUID, RatingChange> changes = new LinkedHashMap<>();
        for (AuthoritativePlayerOutcome outcome : match.outcomes()) {
            SeasonRating own = playerRatings.get(outcome.userId());
            List<SeasonRating> opponents = playerRatings.values().stream()
                    .filter(candidate -> !candidate.userId().equals(outcome.userId())).toList();
            double expected = opponents.stream()
                    .mapToDouble(opponent -> 1.0 / (1.0 + Math.pow(10.0,
                            (ratingsBeforeMatch.get(opponent.userId())
                                    - ratingsBeforeMatch.get(own.userId())) / 400.0)))
                    .average().orElse(0.5);
            double actual = outcome.result() == GameResultType.WIN ? 1.0
                    : outcome.result() == GameResultType.DRAW ? 0.5 : 0.0;
            int before = ratingsBeforeMatch.get(own.userId());
            int after = Math.max(0, (int) Math.round(before + K_FACTOR * (actual - expected)));
            own.apply(after, outcome.result(), now);
            changes.put(outcome.userId(), new RatingChange(before, after));
        }
        ratings.saveAll(playerRatings.values());
        return Map.copyOf(changes);
    }

    private SeasonRating requireRating(Season season, UserAccount user, Game game, Instant now) {
        return ratings.findBySeasonIdAndUserIdAndGameId(season.id(), user.getId(), game.getId())
                .orElseGet(() -> ratings.save(SeasonRating.create(season, user, game, now)));
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
