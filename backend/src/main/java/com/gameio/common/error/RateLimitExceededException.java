package com.gameio.common.error;

import org.springframework.http.HttpStatus;

public final class RateLimitExceededException extends ApiException {
    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Too many login attempts. Try again later.");
    }
}
