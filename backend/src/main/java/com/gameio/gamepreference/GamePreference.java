package com.gameio.gamepreference;

import com.gameio.game.Game;
import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_game_preferences")
class GamePreference {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GamePreference() {
    }

    private GamePreference(UserAccount user, Game game, Instant now) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.game = game;
        this.favorite = false;
        this.updatedAt = now;
    }

    static GamePreference create(UserAccount user, Game game, Instant now) {
        return new GamePreference(user, game, now);
    }

    void setFavorite(boolean favorite, Instant now) {
        this.favorite = favorite;
        this.updatedAt = now;
    }

    void markPlayed(Instant now) {
        this.lastPlayedAt = now;
        this.updatedAt = now;
    }

    UUID gameId() {
        return game.getId();
    }

    String gameSlug() {
        return game.getSlug();
    }

    boolean favorite() {
        return favorite;
    }

    Instant lastPlayedAt() {
        return lastPlayedAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
