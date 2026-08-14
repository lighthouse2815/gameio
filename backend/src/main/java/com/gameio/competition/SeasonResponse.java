package com.gameio.competition;

import java.time.Instant;
import java.util.UUID;

public record SeasonResponse(UUID id, String code, String name, Instant startsAt, Instant endsAt) {
    static SeasonResponse from(Season season) {
        return new SeasonResponse(season.id(), season.code(), season.name(), season.startsAt(), season.endsAt());
    }
}
