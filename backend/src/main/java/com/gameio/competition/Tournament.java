package com.gameio.competition;

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
import java.util.UUID;

@Entity
@Table(name = "tournaments")
class Tournament {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "game_id", nullable = false) private Game game;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private UserAccount createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TournamentStatus status;
    @Column(name = "max_players", nullable = false) private int maxPlayers;
    @Column(name = "current_round", nullable = false) private int currentRound;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "winner_user_id") private UserAccount winner;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version @Column(name = "entity_version", nullable = false) private long version;

    protected Tournament() {
    }

    static Tournament create(String name, Game game, UserAccount creator, int maxPlayers, Instant now) {
        Tournament tournament = new Tournament();
        tournament.id = UUID.randomUUID();
        tournament.name = name;
        tournament.game = game;
        tournament.createdBy = creator;
        tournament.status = TournamentStatus.REGISTRATION;
        tournament.maxPlayers = maxPlayers;
        tournament.createdAt = now;
        return tournament;
    }

    void startRound(int round, Instant now) {
        if (status == TournamentStatus.REGISTRATION) {
            status = TournamentStatus.IN_PROGRESS;
            startedAt = now;
        }
        currentRound = round;
    }

    void complete(UserAccount champion, Instant now) {
        status = TournamentStatus.COMPLETED;
        winner = champion;
        completedAt = now;
    }

    UUID id() { return id; }
    String name() { return name; }
    Game game() { return game; }
    UserAccount createdBy() { return createdBy; }
    TournamentStatus status() { return status; }
    int maxPlayers() { return maxPlayers; }
    int currentRound() { return currentRound; }
    UserAccount winner() { return winner; }
    Instant createdAt() { return createdAt; }
    Instant startedAt() { return startedAt; }
    Instant completedAt() { return completedAt; }
}
