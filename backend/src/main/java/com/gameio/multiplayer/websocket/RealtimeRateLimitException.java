package com.gameio.multiplayer.websocket;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

public final class RealtimeRateLimitException extends ApiException {
    public RealtimeRateLimitException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "REALTIME_RATE_LIMITED",
                "Realtime message rate exceeded. Slow down and retry.");
    }
}
