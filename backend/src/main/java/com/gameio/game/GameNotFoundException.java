package com.gameio.game;

import com.gameio.common.error.NotFoundException;

public final class GameNotFoundException extends NotFoundException {
    public GameNotFoundException() {
        super("GAME_NOT_FOUND", "Game was not found or is disabled");
    }
}
