package com.gameio.common.error;

import org.springframework.http.HttpStatus;

public final class UnauthorizedException extends ApiException {
    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
