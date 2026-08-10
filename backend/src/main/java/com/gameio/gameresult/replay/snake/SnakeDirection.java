package com.gameio.gameresult.replay.snake;

enum SnakeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    boolean isOpposite(SnakeDirection other) {
        return this == UP && other == DOWN
                || this == DOWN && other == UP
                || this == LEFT && other == RIGHT
                || this == RIGHT && other == LEFT;
    }

    String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
