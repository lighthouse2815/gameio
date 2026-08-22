package com.gameio.multiplayer.engine.dotsboxes;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DotsBoxesEngine implements AuthoritativeEngine {
    static final int DOTS = 5;
    static final int BOXES = DOTS - 1;
    static final int TOTAL_EDGES = DOTS * BOXES * 2;

    private final List<UUID> players;
    private final boolean[][] horizontalEdges = new boolean[DOTS][BOXES];
    private final boolean[][] verticalEdges = new boolean[BOXES][DOTS];
    private final int[][] boxes = new int[BOXES][BOXES];
    private final int[] scores = new int[2];
    private int currentPlayer;
    private long sequence;
    private EdgeMove lastEdge;
    private UUID winner;
    private boolean draw;

    public DotsBoxesEngine(List<UUID> playerIds) {
        validatePlayers(playerIds);
        players = List.copyOf(playerIds);
    }

    DotsBoxesEngine(List<UUID> playerIds, DotsBoxesCheckpoint checkpoint) {
        this(playerIds);
        Objects.requireNonNull(checkpoint, "Dots and Boxes checkpoint is required");
        if (checkpoint.horizontalEdges().size() != DOTS
                || checkpoint.horizontalEdges().stream().anyMatch(row -> row.size() != BOXES)
                || checkpoint.verticalEdges().size() != BOXES
                || checkpoint.verticalEdges().stream().anyMatch(row -> row.size() != DOTS)
                || checkpoint.boxes().size() != BOXES
                || checkpoint.boxes().stream().anyMatch(row -> row.size() != BOXES)
                || checkpoint.scores().size() != 2
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.sequence() < 0 || checkpoint.sequence() > TOTAL_EDGES
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.winnerId() != null && checkpoint.draw()) {
            throw new IllegalArgumentException("Dots and Boxes checkpoint is invalid");
        }

        int edgeCount = 0;
        for (int row = 0; row < DOTS; row++) {
            for (int column = 0; column < BOXES; column++) {
                Boolean value = checkpoint.horizontalEdges().get(row).get(column);
                if (value == null) throw new IllegalArgumentException("Dots and Boxes edge is invalid");
                horizontalEdges[row][column] = value;
                if (value) edgeCount++;
            }
        }
        for (int row = 0; row < BOXES; row++) {
            for (int column = 0; column < DOTS; column++) {
                Boolean value = checkpoint.verticalEdges().get(row).get(column);
                if (value == null) throw new IllegalArgumentException("Dots and Boxes edge is invalid");
                verticalEdges[row][column] = value;
                if (value) edgeCount++;
            }
        }
        if (edgeCount != checkpoint.sequence()) {
            throw new IllegalArgumentException("Dots and Boxes edge count is invalid");
        }

        int[] countedScores = new int[2];
        for (int row = 0; row < BOXES; row++) {
            for (int column = 0; column < BOXES; column++) {
                int owner = checkpoint.boxes().get(row).get(column);
                if (owner < 0 || owner > 2) {
                    throw new IllegalArgumentException("Dots and Boxes owner is invalid");
                }
                boxes[row][column] = owner;
                boolean closed = boxClosed(row, column);
                if (closed != (owner != 0)) {
                    throw new IllegalArgumentException("Dots and Boxes box ownership is inconsistent");
                }
                if (owner != 0) countedScores[owner - 1]++;
            }
        }
        for (int index = 0; index < 2; index++) {
            int score = checkpoint.scores().get(index);
            if (score < 0 || score != countedScores[index]) {
                throw new IllegalArgumentException("Dots and Boxes score is invalid");
            }
            scores[index] = score;
        }

        currentPlayer = checkpoint.currentPlayer();
        sequence = checkpoint.sequence();
        lastEdge = checkpoint.lastEdge();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        validateRestoredState();
    }

    @Override
    public DotsBoxesSnapshot snapshot() {
        List<List<Boolean>> horizontal = new ArrayList<>(DOTS);
        for (boolean[] cells : horizontalEdges) {
            List<Boolean> row = new ArrayList<>(BOXES);
            for (boolean cell : cells) row.add(cell);
            horizontal.add(List.copyOf(row));
        }
        List<List<Boolean>> vertical = new ArrayList<>(BOXES);
        for (boolean[] cells : verticalEdges) {
            List<Boolean> row = new ArrayList<>(DOTS);
            for (boolean cell : cells) row.add(cell);
            vertical.add(List.copyOf(row));
        }
        List<List<String>> ownedBoxes = new ArrayList<>(BOXES);
        for (int[] cells : boxes) {
            List<String> row = new ArrayList<>(BOXES);
            for (int cell : cells) row.add(cell == 0 ? "" : cell == 1 ? "R" : "B");
            ownedBoxes.add(List.copyOf(row));
        }
        return new DotsBoxesSnapshot(sequence, List.copyOf(horizontal), List.copyOf(vertical),
                List.copyOf(ownedBoxes), List.of(scores[0], scores[1]),
                terminal() ? List.of() : legalMoves(), lastEdge,
                terminal() ? null : players.get(currentPlayer), winner, draw);
    }

    @Override
    public DotsBoxesCheckpoint checkpoint() {
        List<List<Boolean>> horizontal = new ArrayList<>(DOTS);
        for (boolean[] cells : horizontalEdges) {
            List<Boolean> row = new ArrayList<>(BOXES);
            for (boolean cell : cells) row.add(cell);
            horizontal.add(List.copyOf(row));
        }
        List<List<Boolean>> vertical = new ArrayList<>(BOXES);
        for (boolean[] cells : verticalEdges) {
            List<Boolean> row = new ArrayList<>(DOTS);
            for (boolean cell : cells) row.add(cell);
            vertical.add(List.copyOf(row));
        }
        List<List<Integer>> owners = new ArrayList<>(BOXES);
        for (int[] cells : boxes) {
            List<Integer> row = new ArrayList<>(BOXES);
            for (int cell : cells) row.add(cell);
            owners.add(List.copyOf(row));
        }
        return new DotsBoxesCheckpoint(DotsBoxesCheckpoint.CURRENT_VERSION, sequence,
                List.copyOf(horizontal), List.copyOf(vertical), List.copyOf(owners),
                List.of(scores[0], scores[1]), currentPlayer, lastEdge, winner, draw);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        boolean horizontal = "DRAW_HORIZONTAL".equals(input.action());
        boolean vertical = "DRAW_VERTICAL".equals(input.action());
        if (!horizontal && !vertical) {
            throw new InvalidGameActionException("Dots and Boxes accepts DRAW_HORIZONTAL or DRAW_VERTICAL");
        }
        if (input.sequence() != null || input.character() != null) {
            throw new InvalidGameActionException("Dots and Boxes accepts edge coordinates only");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int row = coordinate(input.row(), horizontal ? DOTS : BOXES);
        int column = coordinate(input.column(), horizontal ? BOXES : DOTS);
        if (horizontal ? horizontalEdges[row][column] : verticalEdges[row][column]) {
            throw new InvalidGameActionException("Edge is already drawn");
        }

        if (horizontal) horizontalEdges[row][column] = true;
        else verticalEdges[row][column] = true;
        lastEdge = new EdgeMove(horizontal ? "H" : "V", row, column);
        sequence++;

        int captured = horizontal
                ? captureHorizontalAdjacentBoxes(row, column)
                : captureVerticalAdjacentBoxes(row, column);
        if (sequence == TOTAL_EDGES) finishByScore();
        else if (captured == 0) currentPlayer = 1 - currentPlayer;

        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        if (!terminal()) return List.of();
        return List.of(
                new EngineOutcome(players.getFirst(), draw ? GameResultType.DRAW
                        : winner.equals(players.getFirst()) ? GameResultType.WIN : GameResultType.LOSS, scores[0]),
                new EngineOutcome(players.get(1), draw ? GameResultType.DRAW
                        : winner.equals(players.get(1)) ? GameResultType.WIN : GameResultType.LOSS, scores[1]));
    }

    private List<EdgeMove> legalMoves() {
        List<EdgeMove> moves = new ArrayList<>(TOTAL_EDGES - Math.toIntExact(sequence));
        for (int row = 0; row < DOTS; row++) {
            for (int column = 0; column < BOXES; column++) {
                if (!horizontalEdges[row][column]) moves.add(new EdgeMove("H", row, column));
            }
        }
        for (int row = 0; row < BOXES; row++) {
            for (int column = 0; column < DOTS; column++) {
                if (!verticalEdges[row][column]) moves.add(new EdgeMove("V", row, column));
            }
        }
        return List.copyOf(moves);
    }

    private int captureHorizontalAdjacentBoxes(int row, int column) {
        int captured = 0;
        if (row > 0) captured += captureBox(row - 1, column);
        if (row < BOXES) captured += captureBox(row, column);
        return captured;
    }

    private int captureVerticalAdjacentBoxes(int row, int column) {
        int captured = 0;
        if (column > 0) captured += captureBox(row, column - 1);
        if (column < BOXES) captured += captureBox(row, column);
        return captured;
    }

    private int captureBox(int row, int column) {
        if (boxes[row][column] == 0 && boxClosed(row, column)) {
            boxes[row][column] = currentPlayer + 1;
            scores[currentPlayer]++;
            return 1;
        }
        return 0;
    }

    private boolean boxClosed(int row, int column) {
        return horizontalEdges[row][column]
                && horizontalEdges[row + 1][column]
                && verticalEdges[row][column]
                && verticalEdges[row][column + 1];
    }

    private void finishByScore() {
        if (scores[0] == scores[1]) draw = true;
        else winner = players.get(scores[0] > scores[1] ? 0 : 1);
    }

    private void validateRestoredState() {
        boolean expectedTerminal = sequence == TOTAL_EDGES;
        UUID expectedWinner = expectedTerminal && scores[0] != scores[1]
                ? players.get(scores[0] > scores[1] ? 0 : 1) : null;
        boolean expectedDraw = expectedTerminal && scores[0] == scores[1];
        if (terminal() != expectedTerminal
                || !Objects.equals(winner, expectedWinner)
                || draw != expectedDraw
                || sequence == 0 && lastEdge != null
                || sequence > 0 && !lastEdgeValid()) {
            throw new IllegalArgumentException("Dots and Boxes checkpoint outcome is invalid");
        }
    }

    private boolean lastEdgeValid() {
        if (lastEdge == null) return false;
        if ("H".equals(lastEdge.orientation())) {
            return lastEdge.row() >= 0 && lastEdge.row() < DOTS
                    && lastEdge.column() >= 0 && lastEdge.column() < BOXES
                    && horizontalEdges[lastEdge.row()][lastEdge.column()];
        }
        return lastEdge.row() >= 0 && lastEdge.row() < BOXES
                && lastEdge.column() >= 0 && lastEdge.column() < DOTS
                && verticalEdges[lastEdge.row()][lastEdge.column()];
    }

    private int coordinate(Integer value, int exclusiveMaximum) {
        if (value == null || value < 0 || value >= exclusiveMaximum) {
            throw new InvalidGameActionException("Edge coordinate is outside the Dots and Boxes board");
        }
        return value;
    }

    private static void validatePlayers(List<UUID> playerIds) {
        if (playerIds == null || playerIds.size() != 2
                || playerIds.stream().anyMatch(Objects::isNull)
                || playerIds.stream().distinct().count() != 2) {
            throw new IllegalArgumentException("Dots and Boxes requires two distinct players");
        }
    }
}
