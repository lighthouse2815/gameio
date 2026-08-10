package com.gameio.auth;

import com.gameio.common.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
class LoginRateLimiter {
    private static final int MAX_FAILURES = 10;
    private static final int MAX_TRACKED_CLIENTS = 50_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final AtomicLong recordedFailures = new AtomicLong();
    private final Clock clock;

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void check(String clientKey) {
        Deque<Instant> attempts = failures.get(clientKey);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            removeExpired(attempts);
            if (attempts.isEmpty()) {
                failures.remove(clientKey, attempts);
                return;
            }
            if (attempts.size() >= MAX_FAILURES) {
                throw new RateLimitExceededException();
            }
        }
    }

    void recordFailure(String clientKey) {
        if (recordedFailures.incrementAndGet() % 256 == 0) {
            cleanupExpiredEntries();
        }
        if (!failures.containsKey(clientKey) && failures.size() >= MAX_TRACKED_CLIENTS) {
            cleanupExpiredEntries();
            if (failures.size() >= MAX_TRACKED_CLIENTS) {
                throw new RateLimitExceededException();
            }
        }
        Deque<Instant> attempts = failures.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            removeExpired(attempts);
            attempts.addLast(Instant.now(clock));
        }
    }

    void reset(String clientKey) {
        failures.remove(clientKey);
    }

    private void removeExpired(Deque<Instant> attempts) {
        Instant cutoff = Instant.now(clock).minus(WINDOW);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private void cleanupExpiredEntries() {
        failures.forEach((key, attempts) -> {
            synchronized (attempts) {
                removeExpired(attempts);
                if (attempts.isEmpty()) {
                    failures.remove(key, attempts);
                }
            }
        });
    }
}
