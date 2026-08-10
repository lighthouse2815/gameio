package com.gameio.matchmaking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisMatchmakingStore implements MatchmakingStore {
    private static final String QUEUE_PREFIX = "gameio:mm:queue:";
    private static final String USER_PREFIX = "gameio:mm:user:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisMatchmakingStore(StringRedisTemplate redis, ObjectMapper objectMapper, Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void enqueue(MatchmakingTicket ticket) {
        writeTicket(ticket);
        redis.opsForZSet().add(queueKey(ticket.gameId()), ticket.userId().toString(), ticket.joinedAt().toEpochMilli());
    }

    @Override
    public Optional<MatchmakingTicket> findByUser(UUID userId) {
        String json = redis.opsForValue().get(USER_PREFIX + userId);
        if (json == null) return Optional.empty();
        try {
            MatchmakingTicket ticket = objectMapper.readValue(json, MatchmakingTicket.class);
            if (!ticket.expiresAt().isAfter(Instant.now(clock))) {
                remove(userId);
                return Optional.empty();
            }
            return Optional.of(ticket);
        } catch (JacksonException exception) {
            redis.delete(USER_PREFIX + userId);
            return Optional.empty();
        }
    }

    @Override
    public List<MatchmakingTicket> takeOldest(UUID gameId, int count) {
        Set<String> userIds = redis.opsForZSet().range(queueKey(gameId), 0, count - 1L);
        if (userIds == null || userIds.size() < count) {
            return List.of();
        }
        List<MatchmakingTicket> tickets = new ArrayList<>();
        for (String userId : userIds) {
            UUID id;
            try {
                id = UUID.fromString(userId);
            } catch (IllegalArgumentException exception) {
                redis.opsForZSet().remove(queueKey(gameId), userId);
                continue;
            }
            Optional<MatchmakingTicket> ticket = findByUser(id)
                    .filter(value -> value.status() == MatchmakingStatus.QUEUED && value.gameId().equals(gameId));
            if (ticket.isPresent()) {
                tickets.add(ticket.get());
            } else {
                redis.opsForZSet().remove(queueKey(gameId), userId);
            }
        }
        if (tickets.size() < count) {
            return List.of();
        }
        redis.opsForZSet().remove(queueKey(gameId), tickets.stream()
                .map(ticket -> ticket.userId().toString()).toArray());
        return List.copyOf(tickets);
    }

    @Override
    public void saveMatched(MatchmakingTicket ticket) {
        writeTicket(ticket);
    }

    @Override
    public void remove(UUID userId) {
        findRaw(userId).ifPresent(ticket ->
                redis.opsForZSet().remove(queueKey(ticket.gameId()), userId.toString()));
        redis.delete(USER_PREFIX + userId);
    }

    private void writeTicket(MatchmakingTicket ticket) {
        Duration ttl = Duration.between(Instant.now(clock), ticket.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) return;
        try {
            redis.opsForValue().set(USER_PREFIX + ticket.userId(), objectMapper.writeValueAsString(ticket), ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize matchmaking ticket", exception);
        }
    }

    private Optional<MatchmakingTicket> findRaw(UUID userId) {
        String json = redis.opsForValue().get(USER_PREFIX + userId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, MatchmakingTicket.class));
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }

    private String queueKey(UUID gameId) {
        return QUEUE_PREFIX + gameId;
    }
}
