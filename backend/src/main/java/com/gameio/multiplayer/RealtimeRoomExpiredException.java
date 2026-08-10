package com.gameio.multiplayer;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

public final class RealtimeRoomExpiredException extends ApiException {
    public RealtimeRoomExpiredException() {
        super(HttpStatus.GONE, "ROOM_EXPIRED",
                "Active engine state expired after a server restart; join a new room");
    }
}
