package com.gameio.room;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

public final class InvalidRoomActionException extends ApiException {
    public InvalidRoomActionException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
