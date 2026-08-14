package com.gameio.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class RedisActiveMatchStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void roundTripsCheckpointAndCapsItsTtlAtThirtyFiveMinutes() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisActiveMatchStore store = new RedisActiveMatchStore(redis, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "SAVE01", UUID.randomUUID(), "tic-tac-toe", "Tic Tac Toe",
                first, 2, 2, false, RoomStatus.PLAYING,
                List.of(new RoomPlayer(first, "First", true, true, true),
                        new RoomPlayer(second, "Second", true, false, true)),
                NOW, NOW.plus(Duration.ofHours(6)));
        ActiveMatchCheckpoint checkpoint = new ActiveMatchCheckpoint(UUID.randomUUID(), room, NOW,
                NOW.plusSeconds(12), Map.of(second, NOW.plusSeconds(5)),
                JsonMapper.builder().build().createObjectNode().put("sequence", 3), NOW.plusSeconds(12));

        store.save(checkpoint);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("gameio:active-match:" + roomId), json.capture(), eq(Duration.ofMinutes(35)));
        when(values.get("gameio:active-match:" + roomId)).thenReturn(json.getValue());
        assertThat(store.find(roomId)).contains(checkpoint);
    }

    @Test
    void discardsUnreadableCheckpoint() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UUID roomId = UUID.randomUUID();
        when(values.get("gameio:active-match:" + roomId)).thenReturn("{not-json");
        RedisActiveMatchStore store = new RedisActiveMatchStore(redis, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(store.find(roomId)).isEmpty();
        verify(redis).delete("gameio:active-match:" + roomId);
    }
}
