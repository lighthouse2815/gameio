package com.gameio.room;

import com.gameio.common.error.NotFoundException;

public final class RoomNotFoundException extends NotFoundException {
    public RoomNotFoundException() {
        super("ROOM_NOT_FOUND", "Room was not found or has expired");
    }
}
