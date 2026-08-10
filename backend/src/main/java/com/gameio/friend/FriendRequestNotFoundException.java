package com.gameio.friend;

import com.gameio.common.error.NotFoundException;

final class FriendRequestNotFoundException extends NotFoundException {
    FriendRequestNotFoundException() {
        super("FRIEND_REQUEST_NOT_FOUND", "Friend request was not found");
    }
}
