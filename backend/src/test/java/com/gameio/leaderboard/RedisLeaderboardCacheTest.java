package com.gameio.leaderboard;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class RedisLeaderboardCacheTest {
    @Test
    void writesBoundedEntriesAndInvalidatesGlobalAndGameGenerations() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        RedisLeaderboardCache cache = new RedisLeaderboardCache(redis, JsonMapper.builder().build());
        LeaderboardResponse response = new LeaderboardResponse(List.of(), 0, 20, 0, 0);

        cache.putGlobal(0, 20, response);
        UUID gameId = UUID.randomUUID();
        cache.invalidate(gameId);

        verify(values).set(eq("gameio:leaderboard:cache:global:v0:p0:s20"), anyString(),
                eq(Duration.ofSeconds(30)));
        verify(values).increment("gameio:leaderboard:version:global");
        verify(values).increment("gameio:leaderboard:version:game:" + gameId);
    }
}
