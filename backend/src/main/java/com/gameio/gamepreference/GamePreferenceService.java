package com.gameio.gamepreference;

import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GamePreferenceService {
    private final GamePreferenceRepository preferences;
    private final GameRepository games;
    private final UserRepository users;
    private final Clock clock;

    GamePreferenceService(
            GamePreferenceRepository preferences,
            GameRepository games,
            UserRepository users,
            Clock clock) {
        this.preferences = preferences;
        this.games = games;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<GamePreferenceResponse> list(UUID userId) {
        return preferences.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(GamePreferenceResponse::from)
                .toList();
    }

    @Transactional
    GamePreferenceResponse updateFavorite(UUID userId, UUID gameId, boolean favorite) {
        Instant now = Instant.now(clock);
        GamePreference preference = findOrCreate(userId, gameId, now);
        preference.setFavorite(favorite, now);
        return GamePreferenceResponse.from(preferences.save(preference));
    }

    @Transactional
    GamePreferenceResponse markPlayed(UUID userId, UUID gameId) {
        Instant now = Instant.now(clock);
        GamePreference preference = findOrCreate(userId, gameId, now);
        preference.markPlayed(now);
        return GamePreferenceResponse.from(preferences.save(preference));
    }

    private GamePreference findOrCreate(UUID userId, UUID gameId, Instant now) {
        return preferences.findByUserIdAndGameId(userId, gameId).orElseGet(() -> {
            UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
            Game game = games.findById(gameId).filter(Game::isEnabled).orElseThrow(GameNotFoundException::new);
            return GamePreference.create(user, game, now);
        });
    }
}
