package com.gameio.gameresult;

import com.gameio.game.Game;
import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
public class GameSession {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private UserAccount player;

    @Column(name = "random_seed", nullable = false)
    private long randomSeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "challenge_date")
    private LocalDate challengeDate;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long version;

    protected GameSession() {
    }

    private GameSession(Game game, UserAccount player, long randomSeed, Instant startedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.game = game;
        this.player = player;
        this.randomSeed = randomSeed;
        this.status = GameSessionStatus.RUNNING;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public static GameSession start(
            Game game, UserAccount player, long randomSeed, Instant startedAt, Instant expiresAt) {
        return new GameSession(game, player, randomSeed, startedAt, expiresAt);
    }

    public static GameSession startDailyChallenge(
            Game game,
            UserAccount player,
            long randomSeed,
            LocalDate challengeDate,
            Instant startedAt,
            Instant expiresAt) {
        GameSession session = new GameSession(game, player, randomSeed, startedAt, expiresAt);
        session.challengeDate = challengeDate;
        return session;
    }

    public void complete(Instant now) {
        if (status != GameSessionStatus.RUNNING) {
            throw new IllegalStateException("Only a running game session can be completed");
        }
        this.status = GameSessionStatus.COMPLETED;
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public UserAccount getPlayer() {
        return player;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public GameSessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public LocalDate getChallengeDate() {
        return challengeDate;
    }
}
