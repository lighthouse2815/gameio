package com.gameio.friend;

import com.gameio.common.error.ApiException;
import org.springframework.http.HttpStatus;

public final class AlreadyFriendsException extends ApiException {
    public AlreadyFriendsException() {
        super(HttpStatus.CONFLICT, "ALREADY_FRIENDS", "These players are already friends");
    }
}
