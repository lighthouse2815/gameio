package com.gameio.competition;

import com.gameio.game.Game;
import com.gameio.gameresult.GameResultType;
import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "season_ratings")
class SeasonRating {
    static final int INITIAL_RATING = 1000;

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "season_id") private Season season;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "game_id") private Game game;
    @Column(nullable = false) private int rating;
    @Column(name = "games_played", nullable = false) private int gamesPlayed;
    @Column(nullable = false) private int wins;
    @Column(nullable = false) private int losses;
    @Column(nullable = false) private int draws;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "entity_version", nullable = false) private long version;

    protected SeasonRating() {
    }

    static SeasonRating create(Season season, UserAccount user, Game game, Instant now) {
        SeasonRating rating = new SeasonRating();
        rating.id = UUID.randomUUID();
        rating.season = season;
        rating.user = user;
        rating.game = game;
        rating.rating = INITIAL_RATING;
        rating.updatedAt = now;
        return rating;
    }

    void apply(int nextRating, GameResultType result, Instant now) {
        rating = Math.max(0, nextRating);
        gamesPlayed++;
        if (result == GameResultType.WIN) wins++;
        else if (result == GameResultType.LOSS) losses++;
        else draws++;
        updatedAt = now;
    }

    UUID userId() { return user.getId(); }
    String username() { return user.getUsername(); }
    String avatarUrl() { return user.getAvatarUrl(); }
    UUID gameId() { return game.getId(); }
    String gameSlug() { return game.getSlug(); }
    int rating() { return rating; }
    int gamesPlayed() { return gamesPlayed; }
    int wins() { return wins; }
    int losses() { return losses; }
    int draws() { return draws; }
    Instant updatedAt() { return updatedAt; }
}
