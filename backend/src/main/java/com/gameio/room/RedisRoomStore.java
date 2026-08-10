package com.gameio.room;

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
public class RedisRoomStore implements RoomStore {
    private static final String ROOM_PREFIX = "gameio:room:";
    private static final String CODE_PREFIX = "gameio:room-code:";
    private static final String ROOM_INDEX = "gameio:rooms";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisRoomStore(StringRedisTemplate redis, ObjectMapper objectMapper, Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void save(RoomState room) {
        Duration ttl = Duration.between(Instant.now(clock), room.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            delete(room);
            return;
        }
        try {
            String roomId = room.roomId().toString();
            redis.opsForValue().set(ROOM_PREFIX + roomId, objectMapper.writeValueAsString(room), ttl);
            redis.opsForValue().set(CODE_PREFIX + room.roomCode(), roomId, ttl);
            redis.opsForZSet().add(ROOM_INDEX, roomId, room.createdAt().toEpochMilli());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize room metadata", exception);
        }
    }

    @Override
    public Optional<RoomState> findById(UUID roomId) {
        String json = redis.opsForValue().get(ROOM_PREFIX + roomId);
        if (json == null) {
            redis.opsForZSet().remove(ROOM_INDEX, roomId.toString());
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RoomState.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize room metadata", exception);
        }
    }

    @Override
    public Optional<RoomState> findByCode(String roomCode) {
        String roomId = redis.opsForValue().get(CODE_PREFIX + roomCode);
        if (roomId == null) {
            return Optional.empty();
        }
        try {
            return findById(UUID.fromString(roomId));
        } catch (IllegalArgumentException exception) {
            redis.delete(CODE_PREFIX + roomCode);
            return Optional.empty();
        }
    }

    @Override
    public List<RoomState> findAll() {
        Set<String> ids = redis.opsForZSet().range(ROOM_INDEX, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<RoomState> rooms = new ArrayList<>();
        for (String id : ids) {
            try {
                findById(UUID.fromString(id)).ifPresent(rooms::add);
            } catch (IllegalArgumentException exception) {
                redis.opsForZSet().remove(ROOM_INDEX, id);
            }
        }
        return List.copyOf(rooms);
    }

    @Override
    public void delete(RoomState room) {
        redis.delete(List.of(ROOM_PREFIX + room.roomId(), CODE_PREFIX + room.roomCode()));
        redis.opsForZSet().remove(ROOM_INDEX, room.roomId().toString());
    }
}
