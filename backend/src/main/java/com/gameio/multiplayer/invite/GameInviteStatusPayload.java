package com.gameio.multiplayer.invite;

import com.gameio.room.RoomResponse;
import java.util.UUID;

public record GameInviteStatusPayload(
        UUID inviteId,
        String username,
        RoomResponse room
) {
}
