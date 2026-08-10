package com.gameio.friend;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

final class FriendRequestAlreadyExistsException extends ApiException {
    FriendRequestAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "FRIEND_REQUEST_ALREADY_EXISTS",
                "A pending friend request already exists between these players");
    }
}
