package com.gameio.multiplayer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("!test")
public class RedisActiveMatchStore implements ActiveMatchStore {
    private static final Logger log = LoggerFactory.getLogger(RedisActiveMatchStore.class);
    private static final String PREFIX = "gameio:active-match:";
    private static final Duration MAX_TTL = Duration.ofMinutes(35);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisActiveMatchStore(StringRedisTemplate redis, ObjectMapper objectMapper, Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void save(ActiveMatchCheckpoint checkpoint) {
        Instant now = Instant.now(clock);
        Instant expiresAt = checkpoint.room().expiresAt().isBefore(now.plus(MAX_TTL))
                ? checkpoint.room().expiresAt() : now.plus(MAX_TTL);
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            delete(checkpoint.room().roomId());
            return;
        }
        try {
            redis.opsForValue().set(key(checkpoint.room().roomId()),
                    objectMapper.writeValueAsString(checkpoint), ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize active match checkpoint", exception);
        }
    }

    @Override
    public Optional<ActiveMatchCheckpoint> find(UUID roomId) {
        String json = redis.opsForValue().get(key(roomId));
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, ActiveMatchCheckpoint.class));
        } catch (JacksonException exception) {
            log.error("Discarding unreadable active match checkpoint for room {}", roomId, exception);
            delete(roomId);
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID roomId) {
        redis.delete(key(roomId));
    }

    private String key(UUID roomId) {
        return PREFIX + roomId;
    }
}
