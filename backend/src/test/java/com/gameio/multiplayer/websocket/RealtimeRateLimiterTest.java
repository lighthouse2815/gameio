package com.gameio.multiplayer.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeRateLimiterTest {
    @Test
    void limitsGameInputPerAuthenticatedUser() {
        RealtimeRateLimiter limiter = new RealtimeRateLimiter(
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();
        for (int input = 0; input < 60; input++) {
            limiter.checkGameInput(userId);
        }

        assertThatThrownBy(() -> limiter.checkGameInput(userId))
                .isInstanceOf(RealtimeRateLimitException.class);
        assertThatCode(() -> limiter.checkGameInput(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void limitsHandshakeAndMessageFloodsByClientAddress() {
        RealtimeRateLimiter limiter = new RealtimeRateLimiter(
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        for (int handshake = 0; handshake < 60; handshake++) {
            limiter.checkHandshake("203.0.113.9");
        }
        assertThatThrownBy(() -> limiter.checkHandshake("203.0.113.9"))
                .isInstanceOf(RealtimeRateLimitException.class);

        UUID reconnectingUser = UUID.randomUUID();
        for (int handshake = 0; handshake < 20; handshake++) {
            limiter.checkHandshake(reconnectingUser);
        }
        assertThatThrownBy(() -> limiter.checkHandshake(reconnectingUser))
                .isInstanceOf(RealtimeRateLimitException.class);

        for (int message = 0; message < 240; message++) {
            limiter.checkMessage(UUID.randomUUID(), "198.51.100.4");
        }
        assertThatThrownBy(() -> limiter.checkMessage(UUID.randomUUID(), "198.51.100.4"))
                .isInstanceOf(RealtimeRateLimitException.class);
    }

    @Test
    void limitsQuickReactionsWithoutConsumingGameInputQuota() {
        RealtimeRateLimiter limiter = new RealtimeRateLimiter(
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();
        for (int reaction = 0; reaction < 4; reaction++) {
            limiter.checkReaction(userId);
        }

        assertThatThrownBy(() -> limiter.checkReaction(userId))
                .isInstanceOf(RealtimeRateLimitException.class);
        assertThatCode(() -> limiter.checkGameInput(userId)).doesNotThrowAnyException();
    }
}
