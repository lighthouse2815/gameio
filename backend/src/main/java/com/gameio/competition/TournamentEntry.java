package com.gameio.competition;

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
@Table(name = "tournament_entries")
class TournamentEntry {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tournament_id", nullable = false) private Tournament tournament;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "seed_number", nullable = false) private int seedNumber;
    @Column(nullable = false) private boolean eliminated;
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;

    protected TournamentEntry() {
    }

    static TournamentEntry create(Tournament tournament, UserAccount user, int seedNumber, Instant now) {
        TournamentEntry entry = new TournamentEntry();
        entry.id = UUID.randomUUID();
        entry.tournament = tournament;
        entry.user = user;
        entry.seedNumber = seedNumber;
        entry.joinedAt = now;
        return entry;
    }

    void eliminate() { eliminated = true; }
    UserAccount user() { return user; }
    int seedNumber() { return seedNumber; }
    boolean eliminated() { return eliminated; }
    Instant joinedAt() { return joinedAt; }
}
