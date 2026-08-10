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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_results")
public class GameResult {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", unique = true)
    private GameSession session;

    @Column(name = "match_id")
    private UUID matchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private UserAccount player;

    @Column(nullable = false)
    private long score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameResultType result;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    protected GameResult() {
    }

    private GameResult(
            GameSession session, long score, GameResultType result, int durationSeconds, Instant playedAt) {
        this.id = UUID.randomUUID();
        this.session = session;
        this.game = session.getGame();
        this.player = session.getPlayer();
        this.score = score;
        this.result = result;
        this.durationSeconds = durationSeconds;
        this.playedAt = playedAt;
    }

    public static GameResult verifiedCompletion(
            GameSession session, long score, int durationSeconds, Instant playedAt) {
        return new GameResult(session, score, GameResultType.COMPLETED, durationSeconds, playedAt);
    }

    public static GameResult authoritativeMatch(
            UUID matchId,
            Game game,
            UserAccount player,
            long score,
            GameResultType result,
            int durationSeconds,
            Instant playedAt) {
        GameResult gameResult = new GameResult();
        gameResult.id = UUID.randomUUID();
        gameResult.matchId = matchId;
        gameResult.game = game;
        gameResult.player = player;
        gameResult.score = score;
        gameResult.result = result;
        gameResult.durationSeconds = durationSeconds;
        gameResult.playedAt = playedAt;
        return gameResult;
    }

    public UUID getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Game getGame() {
        return game;
    }

    public UserAccount getPlayer() {
        return player;
    }

    public long getScore() {
        return score;
    }

    public GameResultType getResult() {
        return result;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }
}
