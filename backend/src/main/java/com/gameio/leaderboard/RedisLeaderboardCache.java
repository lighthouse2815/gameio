package com.gameio.leaderboard;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!test")
class RedisLeaderboardCache implements LeaderboardCache {
    private static final Logger log = LoggerFactory.getLogger(RedisLeaderboardCache.class);
    private static final String PREFIX = "gameio:leaderboard:";
    private static final Duration ENTRY_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisLeaderboardCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LeaderboardResponse> getGlobal(int page, int size) {
        return read(cacheKey("global", version("global"), page, size));
    }

    @Override
    public void putGlobal(int page, int size, LeaderboardResponse response) {
        write(cacheKey("global", version("global"), page, size), response);
    }

    @Override
    public Optional<LeaderboardResponse> getForGame(UUID gameId, int page, int size) {
        String scope = "game:" + gameId;
        return read(cacheKey(scope, version(scope), page, size));
    }

    @Override
    public void putForGame(UUID gameId, int page, int size, LeaderboardResponse response) {
        String scope = "game:" + gameId;
        write(cacheKey(scope, version(scope), page, size), response);
    }

    @Override
    public void invalidate(UUID gameId) {
        incrementVersion("global");
        incrementVersion("game:" + gameId);
    }

    private Optional<LeaderboardResponse> read(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, LeaderboardResponse.class));
        } catch (JacksonException | DataAccessException exception) {
            log.warn("Leaderboard cache read failed; using PostgreSQL", exception);
            return Optional.empty();
        }
    }

    private void write(String key, LeaderboardResponse response) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(response), ENTRY_TTL);
        } catch (JacksonException | DataAccessException exception) {
            log.warn("Leaderboard cache write failed; response remains available from PostgreSQL", exception);
        }
    }

    private long version(String scope) {
        try {
            String value = redis.opsForValue().get(versionKey(scope));
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException exception) {
            log.warn("Leaderboard cache version read failed; using base generation", exception);
            return 0;
        }
    }

    private void incrementVersion(String scope) {
        try {
            redis.opsForValue().increment(versionKey(scope));
        } catch (DataAccessException exception) {
            log.warn("Leaderboard cache invalidation failed; entries still expire within {} seconds",
                    ENTRY_TTL.toSeconds(), exception);
        }
    }

    private String versionKey(String scope) {
        return PREFIX + "version:" + scope;
    }

    private String cacheKey(String scope, long version, int page, int size) {
        return PREFIX + "cache:" + scope + ":v" + version + ":p" + page + ":s" + size;
    }
}
