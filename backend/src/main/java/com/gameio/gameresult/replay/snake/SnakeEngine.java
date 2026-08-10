package com.gameio.gameresult.replay.snake;

import java.util.ArrayList;
import java.util.List;

final class SnakeEngine {
    static final int WIDTH = 20;
    static final int HEIGHT = 15;

    private final SnakeRandom random;
    private List<SnakePoint> body;
    private SnakeDirection direction = SnakeDirection.RIGHT;
    private SnakePoint food;
    private long score;
    private int tickMs = 150;
    private Status status = Status.PLAYING;

    SnakeEngine(long seed) {
        this.random = new SnakeRandom(seed);
        int centerX = WIDTH / 2;
        int centerY = HEIGHT / 2;
        this.body = new ArrayList<>(List.of(
                new SnakePoint(centerX, centerY),
                new SnakePoint(centerX - 1, centerY),
                new SnakePoint(centerX - 2, centerY)));
        this.food = placeFood();
    }

    int tickMs() {
        return tickMs;
    }

    boolean terminal() {
        return status != Status.PLAYING;
    }

    void step(SnakeDirection requestedDirection) {
        if (terminal()) {
            throw new IllegalStateException("Snake replay contains actions after game over");
        }
        if (requestedDirection.isOpposite(direction)) {
            throw new IllegalArgumentException("Snake cannot reverse direction in one tick");
        }
        direction = requestedDirection;
        SnakePoint head = nextHead(body.getFirst(), direction);
        boolean ate = head.equals(food);
        List<SnakePoint> collisionBody = ate ? body : body.subList(0, body.size() - 1);
        if (outsideBoard(head) || collisionBody.contains(head)) {
            status = Status.OVER;
            return;
        }

        List<SnakePoint> updated = new ArrayList<>(body.size() + (ate ? 1 : 0));
        updated.add(head);
        if (ate) {
            updated.addAll(body);
            score += 10;
        } else {
            updated.addAll(body.subList(0, body.size() - 1));
        }
        body = updated;
        if (ate) {
            food = placeFood();
            tickMs = Math.max(55, 150 - Math.toIntExact(score / 10) * 6);
            if (food == null) {
                status = Status.WON;
            }
        }
    }

    SnakeState state() {
        String directionValue = direction.wireValue();
        return new SnakeState(WIDTH, HEIGHT, body, directionValue, directionValue, food, score, tickMs,
                status.wireValue);
    }

    private SnakePoint nextHead(SnakePoint head, SnakeDirection requestedDirection) {
        return switch (requestedDirection) {
            case UP -> new SnakePoint(head.x(), head.y() - 1);
            case DOWN -> new SnakePoint(head.x(), head.y() + 1);
            case LEFT -> new SnakePoint(head.x() - 1, head.y());
            case RIGHT -> new SnakePoint(head.x() + 1, head.y());
        };
    }

    private boolean outsideBoard(SnakePoint point) {
        return point.x() < 0 || point.y() < 0 || point.x() >= WIDTH || point.y() >= HEIGHT;
    }

    private SnakePoint placeFood() {
        List<SnakePoint> open = new ArrayList<>(WIDTH * HEIGHT - body.size());
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                SnakePoint point = new SnakePoint(x, y);
                if (!body.contains(point)) {
                    open.add(point);
                }
            }
        }
        return open.isEmpty() ? null : open.get(random.nextIndex(open.size()));
    }

    private enum Status {
        PLAYING("playing"),
        OVER("over"),
        WON("won");

        private final String wireValue;

        Status(String wireValue) {
            this.wireValue = wireValue;
        }
    }
}
