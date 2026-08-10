package com.gameio.multiplayer.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisPresenceStore implements PresenceStore {
    private static final String PREFIX = "gameio:presence:";
    private static final Duration TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisPresenceStore(StringRedisTemplate redis, ObjectMapper objectMapper, Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void online(UUID userId, UUID roomId, String gameSlug, String gameName) {
        PresenceState state = new PresenceState(userId, true, roomId, gameSlug, gameName, Instant.now(clock));
        try {
            redis.opsForValue().set(PREFIX + userId, objectMapper.writeValueAsString(state), TTL);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize presence", exception);
        }
    }

    @Override
    public void offline(UUID userId) {
        redis.delete(PREFIX + userId);
    }

    @Override
    public Map<UUID, PresenceState> read(Set<UUID> userIds) {
        Map<UUID, PresenceState> states = new HashMap<>();
        Instant now = Instant.now(clock);
        for (UUID userId : userIds) {
            String json = redis.opsForValue().get(PREFIX + userId);
            if (json == null) {
                states.put(userId, PresenceState.offline(userId, now));
                continue;
            }
            try {
                states.put(userId, objectMapper.readValue(json, PresenceState.class));
            } catch (JacksonException exception) {
                redis.delete(PREFIX + userId);
                states.put(userId, PresenceState.offline(userId, now));
            }
        }
        return Map.copyOf(states);
    }
}
