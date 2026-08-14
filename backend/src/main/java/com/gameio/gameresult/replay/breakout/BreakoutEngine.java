package com.gameio.gameresult.replay.breakout;

import com.gameio.gameresult.replay.SeededRandom;
import java.util.ArrayList;
import java.util.List;

public final class BreakoutEngine {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;
    public static final int TICKS_PER_SECOND = 60;
    public static final int PADDLE_WIDTH = 104;
    public static final int PADDLE_Y = 438;
    public static final int BALL_RADIUS = 8;
    public static final int BRICK_COLUMNS = 7;
    public static final int BRICK_ROWS = 4;
    public static final int BRICK_WIDTH = 72;
    public static final int BRICK_HEIGHT = 22;
    public static final int BRICK_GAP = 8;
    public static final int BRICK_LEFT = 44;
    public static final int BRICK_TOP = 64;

    private static final int PADDLE_SPEED = 7;
    private static final int BALL_SPEED_X = 3;
    private static final int BALL_SPEED_Y = -4;

    private int paddleX;
    private int ballX;
    private int ballY;
    private int velocityX;
    private int velocityY;
    private final boolean[] bricks = new boolean[BRICK_COLUMNS * BRICK_ROWS];
    private long score;
    private int lives = 3;
    private int tick;
    private String status = "playing";

    public BreakoutEngine(long seed) {
        SeededRandom random = new SeededRandom(seed);
        int horizontalDirection = random.nextIndex(2) == 0 ? -1 : 1;
        int offset = random.nextIndex(81) - 40;
        paddleX = (WIDTH - PADDLE_WIDTH) / 2;
        ballX = WIDTH / 2 + offset;
        ballY = 334;
        velocityX = BALL_SPEED_X * horizontalDirection;
        velocityY = BALL_SPEED_Y;
        java.util.Arrays.fill(bricks, true);
    }

    public BreakoutState state() {
        List<Boolean> brickState = new ArrayList<>(bricks.length);
        for (boolean alive : bricks) {
            brickState.add(alive);
        }
        return new BreakoutState(WIDTH, HEIGHT, paddleX, ballX, ballY, velocityX, velocityY,
                brickState, score, lives, tick, status);
    }

    public boolean terminal() {
        return !"playing".equals(status);
    }

    public BreakoutState step(char direction) {
        if (terminal()) {
            return state();
        }
        int paddleDelta = direction == 'L' ? -PADDLE_SPEED : direction == 'R' ? PADDLE_SPEED : 0;
        paddleX = Math.max(0, Math.min(WIDTH - PADDLE_WIDTH, paddleX + paddleDelta));
        int previousBallY = ballY;
        int nextBallX = ballX + velocityX;
        int nextBallY = ballY + velocityY;

        if (nextBallX <= BALL_RADIUS) {
            nextBallX = BALL_RADIUS;
            velocityX = Math.abs(velocityX);
        } else if (nextBallX >= WIDTH - BALL_RADIUS) {
            nextBallX = WIDTH - BALL_RADIUS;
            velocityX = -Math.abs(velocityX);
        }
        if (nextBallY <= BALL_RADIUS) {
            nextBallY = BALL_RADIUS;
            velocityY = Math.abs(velocityY);
        }

        boolean reachedPaddle = velocityY > 0
                && previousBallY + BALL_RADIUS <= PADDLE_Y
                && nextBallY + BALL_RADIUS >= PADDLE_Y
                && nextBallX + BALL_RADIUS >= paddleX
                && nextBallX - BALL_RADIUS <= paddleX + PADDLE_WIDTH;
        if (reachedPaddle) {
            nextBallY = PADDLE_Y - BALL_RADIUS;
            velocityY = -Math.abs(velocityY);
            int paddleCenter = paddleX + PADDLE_WIDTH / 2;
            int influence = ((nextBallX - paddleCenter) * 5) / (PADDLE_WIDTH / 2);
            velocityX = Math.max(-6, Math.min(6, velocityX + influence));
            if (velocityX == 0) {
                velocityX = direction == 'L' ? -2 : 2;
            }
        }

        for (int index = 0; index < bricks.length; index++) {
            if (!bricks[index]) {
                continue;
            }
            int row = index / BRICK_COLUMNS;
            int column = index % BRICK_COLUMNS;
            int brickX = BRICK_LEFT + column * (BRICK_WIDTH + BRICK_GAP);
            int brickY = BRICK_TOP + row * (BRICK_HEIGHT + BRICK_GAP);
            boolean overlaps = nextBallX + BALL_RADIUS >= brickX
                    && nextBallX - BALL_RADIUS <= brickX + BRICK_WIDTH
                    && nextBallY + BALL_RADIUS >= brickY
                    && nextBallY - BALL_RADIUS <= brickY + BRICK_HEIGHT;
            if (!overlaps) {
                continue;
            }
            bricks[index] = false;
            score += 50;
            velocityY = -velocityY;
            nextBallY = previousBallY + velocityY;
            break;
        }

        boolean allCleared = true;
        for (boolean alive : bricks) {
            if (alive) {
                allCleared = false;
                break;
            }
        }
        if (allCleared) {
            score += (long) lives * 250;
            status = "won";
        } else if (nextBallY - BALL_RADIUS > HEIGHT) {
            lives -= 1;
            if (lives <= 0) {
                status = "lost";
            } else {
                nextBallX = WIDTH / 2;
                nextBallY = 334;
                velocityX = tick % 2 == 0 ? BALL_SPEED_X : -BALL_SPEED_X;
                velocityY = BALL_SPEED_Y;
            }
        }

        ballX = nextBallX;
        ballY = nextBallY;
        tick += 1;
        return state();
    }
}
