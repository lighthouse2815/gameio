package com.gameio.competition;

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
@Table(name = "tournament_matches")
class TournamentMatch {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tournament_id", nullable = false) private Tournament tournament;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "bracket_index", nullable = false) private int bracketIndex;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_one_id", nullable = false) private UserAccount playerOne;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "player_two_id") private UserAccount playerTwo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "winner_id") private UserAccount winner;
    @Column(name = "room_id") private UUID roomId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TournamentMatchStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected TournamentMatch() {
    }

    static TournamentMatch active(
            Tournament tournament, int round, int bracketIndex, UserAccount one, UserAccount two,
            UUID roomId, Instant now) {
        TournamentMatch match = base(tournament, round, bracketIndex, one, two, now);
        match.roomId = roomId;
        match.status = TournamentMatchStatus.ACTIVE;
        return match;
    }

    static TournamentMatch bye(
            Tournament tournament, int round, int bracketIndex, UserAccount player, Instant now) {
        TournamentMatch match = base(tournament, round, bracketIndex, player, null, now);
        match.winner = player;
        match.status = TournamentMatchStatus.COMPLETED;
        match.completedAt = now;
        return match;
    }

    private static TournamentMatch base(
            Tournament tournament, int round, int bracketIndex, UserAccount one, UserAccount two, Instant now) {
        TournamentMatch match = new TournamentMatch();
        match.id = UUID.randomUUID();
        match.tournament = tournament;
        match.roundNumber = round;
        match.bracketIndex = bracketIndex;
        match.playerOne = one;
        match.playerTwo = two;
        match.createdAt = now;
        return match;
    }

    void complete(UserAccount winner, Instant now) {
        this.winner = winner;
        this.status = TournamentMatchStatus.COMPLETED;
        this.completedAt = now;
    }

    void replaceRoom(UUID roomId) { this.roomId = roomId; }
    UUID id() { return id; }
    Tournament tournament() { return tournament; }
    int roundNumber() { return roundNumber; }
    int bracketIndex() { return bracketIndex; }
    UserAccount playerOne() { return playerOne; }
    UserAccount playerTwo() { return playerTwo; }
    UserAccount winner() { return winner; }
    UUID roomId() { return roomId; }
    TournamentMatchStatus status() { return status; }
    Instant completedAt() { return completedAt; }
}
