package com.gameio.multiplayer.invite;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisGameInviteStore implements GameInviteStore {
    private static final String PREFIX = "gameio:invite:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisGameInviteStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(GameInvite invite, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + invite.inviteId(), objectMapper.writeValueAsString(invite), ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize game invite", exception);
        }
    }

    @Override
    public Optional<GameInvite> consume(UUID inviteId) {
        String json = redis.opsForValue().getAndDelete(PREFIX + inviteId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, GameInvite.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize game invite", exception);
        }
    }
}
