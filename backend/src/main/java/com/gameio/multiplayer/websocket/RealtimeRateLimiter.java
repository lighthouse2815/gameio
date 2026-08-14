package com.gameio.multiplayer.websocket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class RealtimeRateLimiter {
    private static final Duration MESSAGE_WINDOW = Duration.ofSeconds(1);
    private static final Duration HANDSHAKE_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_MESSAGES_PER_USER = 120;
    private static final int MAX_GAME_INPUTS_PER_USER = 60;
    private static final int MAX_MESSAGES_PER_IP = 240;
    private static final int MAX_HANDSHAKES_PER_IP = 60;
    private static final int MAX_HANDSHAKES_PER_USER = 20;
    private static final Duration REACTION_WINDOW = Duration.ofSeconds(5);
    private static final int MAX_REACTIONS_PER_USER = 4;
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<UUID, UserBucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, WindowBucket> messageIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, WindowBucket> handshakeIpBuckets = new ConcurrentHashMap<>();
    private final Map<UUID, WindowBucket> handshakeUserBuckets = new ConcurrentHashMap<>();
    private final Map<UUID, WindowBucket> reactionUserBuckets = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final Clock clock;

    public RealtimeRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkHandshake(String clientAddress) {
        checkWindow(handshakeIpBuckets, normalizeAddress(clientAddress), HANDSHAKE_WINDOW,
                MAX_HANDSHAKES_PER_IP);
    }

    public void checkHandshake(UUID userId) {
        if (userId == null) throw new RealtimeRateLimitException();
        checkWindow(handshakeUserBuckets, userId, HANDSHAKE_WINDOW, MAX_HANDSHAKES_PER_USER);
    }

    public void checkMessage(UUID userId, String clientAddress) {
        checkUser(userId, false);
        checkWindow(messageIpBuckets, normalizeAddress(clientAddress), MESSAGE_WINDOW, MAX_MESSAGES_PER_IP);
    }

    public void checkGameInput(UUID userId, String clientAddress) {
        checkUser(userId, true);
    }

    public void checkReaction(UUID userId) {
        if (userId == null) throw new RealtimeRateLimitException();
        checkWindow(reactionUserBuckets, userId, REACTION_WINDOW, MAX_REACTIONS_PER_USER);
    }

    void checkMessage(UUID userId) {
        checkMessage(userId, "test-client");
    }

    void checkGameInput(UUID userId) {
        checkGameInput(userId, "test-client");
    }

    private void checkUser(UUID userId, boolean gameInput) {
        if (userId == null) throw new RealtimeRateLimitException();
        periodicCleanup();
        if (!userBuckets.containsKey(userId)) ensureCapacity(userBuckets);
        Instant now = Instant.now(clock);
        UserBucket bucket = userBuckets.computeIfAbsent(userId, ignored -> new UserBucket(now));
        synchronized (bucket) {
            if (!now.isBefore(bucket.windowStartedAt.plus(MESSAGE_WINDOW))) {
                bucket.windowStartedAt = now;
                bucket.messages = 0;
                bucket.gameInputs = 0;
            }
            bucket.lastSeenAt = now;
            if (gameInput) {
                if (bucket.gameInputs >= MAX_GAME_INPUTS_PER_USER) throw new RealtimeRateLimitException();
                bucket.gameInputs++;
            } else {
                if (bucket.messages >= MAX_MESSAGES_PER_USER) throw new RealtimeRateLimitException();
                bucket.messages++;
            }
        }
    }

    private <K> void checkWindow(
            Map<K, WindowBucket> buckets, K key, Duration window, int maximumChecks) {
        periodicCleanup();
        if (!buckets.containsKey(key)) ensureCapacity(buckets);
        Instant now = Instant.now(clock);
        WindowBucket bucket = buckets.computeIfAbsent(key, ignored -> new WindowBucket(now));
        synchronized (bucket) {
            if (!now.isBefore(bucket.windowStartedAt.plus(window))) {
                bucket.windowStartedAt = now;
                bucket.count = 0;
            }
            bucket.lastSeenAt = now;
            if (bucket.count >= maximumChecks) throw new RealtimeRateLimitException();
            bucket.count++;
        }
    }

    private void periodicCleanup() {
        if (checks.incrementAndGet() % 256 != 0) return;
        Instant now = Instant.now(clock);
        cleanup(userBuckets, now.minus(MESSAGE_WINDOW.multipliedBy(2)));
        cleanup(messageIpBuckets, now.minus(MESSAGE_WINDOW.multipliedBy(2)));
        cleanup(handshakeIpBuckets, now.minus(HANDSHAKE_WINDOW.multipliedBy(2)));
        cleanup(handshakeUserBuckets, now.minus(HANDSHAKE_WINDOW.multipliedBy(2)));
        cleanup(reactionUserBuckets, now.minus(REACTION_WINDOW.multipliedBy(2)));
    }

    private <K, T extends TrackedBucket> void ensureCapacity(Map<K, T> buckets) {
        if (buckets.size() < MAX_TRACKED_KEYS) return;
        Instant cutoff = Instant.now(clock).minus(HANDSHAKE_WINDOW.multipliedBy(2));
        cleanup(buckets, cutoff);
        if (buckets.size() >= MAX_TRACKED_KEYS) throw new RealtimeRateLimitException();
    }

    private <K, T extends TrackedBucket> void cleanup(Map<K, T> buckets, Instant cutoff) {
        buckets.forEach((key, bucket) -> {
            synchronized (bucket) {
                if (bucket.lastSeenAt().isBefore(cutoff)) buckets.remove(key, bucket);
            }
        });
    }

    private String normalizeAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }

    private interface TrackedBucket {
        Instant lastSeenAt();
    }

    private static final class UserBucket implements TrackedBucket {
        private Instant windowStartedAt;
        private Instant lastSeenAt;
        private int messages;
        private int gameInputs;

        private UserBucket(Instant now) {
            this.windowStartedAt = now;
            this.lastSeenAt = now;
        }

        @Override
        public Instant lastSeenAt() {
            return lastSeenAt;
        }
    }

    private static final class WindowBucket implements TrackedBucket {
        private Instant windowStartedAt;
        private Instant lastSeenAt;
        private int count;

        private WindowBucket(Instant now) {
            this.windowStartedAt = now;
            this.lastSeenAt = now;
        }

        @Override
        public Instant lastSeenAt() {
            return lastSeenAt;
        }
    }
}
