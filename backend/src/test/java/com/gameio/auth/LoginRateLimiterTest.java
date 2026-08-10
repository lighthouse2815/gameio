package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void blocksClientAfterTenFailuresAndCanReset() {
        LoginRateLimiter limiter = new LoginRateLimiter(
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.check("client");
            limiter.recordFailure("client");
        }

        assertThatThrownBy(() -> limiter.check("client")).isInstanceOf(RateLimitExceededException.class);
        limiter.reset("client");
        assertThatCode(() -> limiter.check("client")).doesNotThrowAnyException();
    }
}
