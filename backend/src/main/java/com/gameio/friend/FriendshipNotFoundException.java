package com.gameio.friend;

import com.gameio.common.error.NotFoundException;

final class FriendshipNotFoundException extends NotFoundException {
    FriendshipNotFoundException() {
        super("FRIENDSHIP_NOT_FOUND", "Accepted friendship was not found");
    }
}
