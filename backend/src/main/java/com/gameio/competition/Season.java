package com.gameio.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seasons")
class Season {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Season() {
    }

    static Season annual(int year, Instant startsAt, Instant endsAt, Instant now) {
        Season season = new Season();
        season.id = UUID.randomUUID();
        season.code = "S" + year;
        season.name = "Season " + year;
        season.startsAt = startsAt;
        season.endsAt = endsAt;
        season.createdAt = now;
        return season;
    }

    UUID id() { return id; }
    String code() { return code; }
    String name() { return name; }
    Instant startsAt() { return startsAt; }
    Instant endsAt() { return endsAt; }
}
