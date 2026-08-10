package com.gameio.common.error;

import org.springframework.http.HttpStatus;

public final class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
