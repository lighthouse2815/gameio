package com.gameio.competition;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SeasonService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SeasonRepository seasons;
    private final Clock clock;

    SeasonService(SeasonRepository seasons, Clock clock) {
        this.seasons = seasons;
        this.clock = clock;
    }

    @Transactional
    synchronized Season current() {
        Instant now = Instant.now(clock);
        return seasons.findFirstByStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(now, now)
                .orElseGet(() -> {
                    int year = LocalDate.now(clock.withZone(BUSINESS_ZONE)).getYear();
                    Instant startsAt = LocalDate.of(year, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
                    Instant endsAt = LocalDate.of(year + 1, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
                    return seasons.save(Season.annual(year, startsAt, endsAt, now));
                });
    }
}
