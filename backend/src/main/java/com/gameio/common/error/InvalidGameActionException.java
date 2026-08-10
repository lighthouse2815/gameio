package com.gameio.common.error;

import org.springframework.http.HttpStatus;

public final class InvalidGameActionException extends ApiException {
    public InvalidGameActionException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_GAME_ACTION", message);
    }
}
