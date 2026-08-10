package com.gameio.user;

import com.gameio.common.error.NotFoundException;

public final class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "User was not found");
    }
}
