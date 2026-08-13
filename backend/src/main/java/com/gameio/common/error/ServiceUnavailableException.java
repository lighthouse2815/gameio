package com.gameio.common.error;

import org.springframework.http.HttpStatus;

public final class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
