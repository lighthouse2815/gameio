package com.gameio.gameresult.replay.flappybird;

import java.util.ArrayList;
import java.util.List;

final class FlappyEngine {
    static final int WIDTH = 640;
    static final int HEIGHT = 480;
    static final int TICK_MS = 50;
    static final int BIRD_X = 160;
    static final int BIRD_HALF_WIDTH = 16;
    static final int BIRD_HALF_HEIGHT = 12;
    static final int PIPE_WIDTH = 76;
    static final int PIPE_GAP = 168;

    private static final int FIXED_POINT_SCALE = 100;
    private static final int GRAVITY = 55;
    private static final int FLAP_VELOCITY = -760;
    private static final int MAX_FALL_VELOCITY = 900;
    private static final int PIPE_SPEED = 4;
    private static final int FIRST_PIPE_X = 520;
    private static final int PIPE_SPACING = 260;
    private static final int INITIAL_PIPE_COUNT = 3;
    private static final int MIN_GAP_CENTER = 128;
    private static final int MAX_GAP_CENTER = 352;

    private final FlappyRandom random;
    private int birdY = HEIGHT / 2 * FIXED_POINT_SCALE;
    private int birdVelocity;
    private List<Pipe> pipes = new ArrayList<>();
    private long score;
    private int tick;
    private Status status = Status.PLAYING;

    FlappyEngine(long seed) {
        random = new FlappyRandom(seed);
        for (int index = 0; index < INITIAL_PIPE_COUNT; index++) {
            pipes.add(new Pipe(FIRST_PIPE_X + index * PIPE_SPACING, nextGapCenter(), false));
        }
    }

    int tickMs() {
        return TICK_MS;
    }

    boolean terminal() {
        return status == Status.OVER;
    }

    void step(FlappyAction action) {
        if (terminal()) {
            throw new IllegalStateException("Flappy Bird replay contains actions after game over");
        }

        birdVelocity = action == FlappyAction.FLAP ? FLAP_VELOCITY : birdVelocity;
        birdVelocity = Math.min(MAX_FALL_VELOCITY, birdVelocity + GRAVITY);
        birdY += birdVelocity;

        List<Pipe> movedPipes = new ArrayList<>(pipes.size());
        for (Pipe pipe : pipes) {
            Pipe moved = new Pipe(pipe.x() - PIPE_SPEED, pipe.gapCenter(), pipe.passed());
            if (!moved.passed() && moved.x() + PIPE_WIDTH < BIRD_X) {
                moved = new Pipe(moved.x(), moved.gapCenter(), true);
                score += 1;
            }
            movedPipes.add(moved);
        }
        pipes = movedPipes;

        while (!pipes.isEmpty() && pipes.getFirst().x() + PIPE_WIDTH < 0) {
            pipes.removeFirst();
            int lastX = pipes.isEmpty() ? FIRST_PIPE_X : pipes.getLast().x();
            pipes.add(new Pipe(lastX + PIPE_SPACING, nextGapCenter(), false));
        }

        int birdLeft = BIRD_X - BIRD_HALF_WIDTH;
        int birdRight = BIRD_X + BIRD_HALF_WIDTH;
        int birdTop = birdY - BIRD_HALF_HEIGHT * FIXED_POINT_SCALE;
        int birdBottom = birdY + BIRD_HALF_HEIGHT * FIXED_POINT_SCALE;
        boolean hitBoundary = birdTop <= 0 || birdBottom >= HEIGHT * FIXED_POINT_SCALE;
        boolean hitPipe = pipes.stream().anyMatch(pipe -> {
            boolean overlapsHorizontally = birdRight > pipe.x() && birdLeft < pipe.x() + PIPE_WIDTH;
            if (!overlapsHorizontally) {
                return false;
            }
            int gapTop = (pipe.gapCenter() - PIPE_GAP / 2) * FIXED_POINT_SCALE;
            int gapBottom = (pipe.gapCenter() + PIPE_GAP / 2) * FIXED_POINT_SCALE;
            return birdTop < gapTop || birdBottom > gapBottom;
        });

        tick += 1;
        if (hitBoundary || hitPipe) {
            status = Status.OVER;
        }
    }

    FlappyState state() {
        List<FlappyPipeState> pipeStates = pipes.stream()
                .map(pipe -> new FlappyPipeState(pipe.x(), pipe.gapCenter(), pipe.passed()))
                .toList();
        return new FlappyState(WIDTH, HEIGHT, BIRD_X, birdY, birdVelocity, pipeStates, score, tick, TICK_MS,
                status.wireValue);
    }

    private int nextGapCenter() {
        return MIN_GAP_CENTER + random.nextIndex(MAX_GAP_CENTER - MIN_GAP_CENTER + 1);
    }

    private record Pipe(int x, int gapCenter, boolean passed) {
    }

    private enum Status {
        PLAYING("playing"),
        OVER("over");

        private final String wireValue;

        Status(String wireValue) {
            this.wireValue = wireValue;
        }
    }
}
