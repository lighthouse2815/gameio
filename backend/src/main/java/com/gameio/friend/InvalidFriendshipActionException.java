package com.gameio.friend;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

final class InvalidFriendshipActionException extends ApiException {
    private InvalidFriendshipActionException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static InvalidFriendshipActionException selfRequest() {
        return new InvalidFriendshipActionException(
                HttpStatus.BAD_REQUEST, "CANNOT_FRIEND_SELF", "A player cannot send a friend request to themselves");
    }

    static InvalidFriendshipActionException notIncoming() {
        return new InvalidFriendshipActionException(
                HttpStatus.FORBIDDEN, "FRIEND_REQUEST_NOT_INCOMING",
                "Only the recipient can accept or reject this friend request");
    }

    static InvalidFriendshipActionException notPending() {
        return new InvalidFriendshipActionException(
                HttpStatus.CONFLICT, "FRIEND_REQUEST_NOT_PENDING", "Friend request is no longer pending");
    }
}
