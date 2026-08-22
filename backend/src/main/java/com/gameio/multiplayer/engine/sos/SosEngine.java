package com.gameio.multiplayer.engine.sos;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class SosEngine implements AuthoritativeEngine {
    static final int SIZE = 6;
    private static final int EMPTY = 0;
    private static final int S = 1;
    private static final int O = 2;
    private static final int[][] AXES = {
            {0, 1}, {1, 0}, {1, 1}, {1, -1}
    };

    private final List<UUID> players;
    private final int[][] board = new int[SIZE][SIZE];
    private final int[] scores = new int[2];
    private final List<SosMoveCheckpoint> moveHistory = new ArrayList<>();
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private boolean draw;
    private Integer lastMoveRow;
    private Integer lastMoveColumn;
    private int lastMovePoints;

    public SosEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.getFirst().equals(playerIds.get(1))) {
            throw new IllegalArgumentException("SOS requires exactly two distinct players");
        }
        players = List.copyOf(playerIds);
    }

    SosEngine(List<UUID> playerIds, SosCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.board().size() != SIZE
                || checkpoint.board().stream().anyMatch(row -> row.size() != SIZE)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.moves() < 0 || checkpoint.moves() > SIZE * SIZE
                || checkpoint.sequence() != checkpoint.moves()
                || checkpoint.moveHistory().size() != checkpoint.moves()
                || checkpoint.scores().size() != 2
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.draw() && checkpoint.winnerId() != null
                || checkpoint.lastMovePoints() < 0) {
            throw new IllegalArgumentException("SOS checkpoint is invalid");
        }

        replayAndValidate(checkpoint);
        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        scores[0] = checkpoint.scores().getFirst();
        scores[1] = checkpoint.scores().get(1);
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        moveHistory.addAll(checkpoint.moveHistory());
        lastMoveRow = checkpoint.lastMoveRow();
        lastMoveColumn = checkpoint.lastMoveColumn();
        lastMovePoints = checkpoint.lastMovePoints();
    }

    @Override
    public SosSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell == EMPTY ? "" : cell == S ? "S" : "O");
            rows.add(List.copyOf(row));
        }
        return new SosSnapshot(sequence, List.copyOf(rows), terminal() ? null : players.get(currentPlayer),
                List.of(new SosPlayerSnapshot(players.getFirst(), scores[0]),
                        new SosPlayerSnapshot(players.get(1), scores[1])),
                winner, draw, lastMoveRow, lastMoveColumn, lastMovePoints);
    }

    @Override
    public SosCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new SosCheckpoint(SosCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, List.of(scores[0], scores[1]), winner, draw,
                List.copyOf(moveHistory), lastMoveRow, lastMoveColumn, lastMovePoints);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        int marker = marker(input.action());
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.contains(userId)) {
            throw new InvalidGameActionException("Player does not belong to this match");
        }
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }

        int row = coordinate(input.row());
        int column = coordinate(input.column());
        if (board[row][column] != EMPTY) {
            throw new InvalidGameActionException("Board cell is already occupied");
        }

        int movingPlayer = currentPlayer;
        board[row][column] = marker;
        int points = countCreatedAt(board, row, column);
        scores[movingPlayer] += points;
        moves++;
        sequence++;
        lastMoveRow = row;
        lastMoveColumn = column;
        lastMovePoints = points;
        moveHistory.add(new SosMoveCheckpoint(movingPlayer, marker, row, column, points));

        if (moves == SIZE * SIZE) finishByScore();
        else if (points == 0) currentPlayer = 1 - currentPlayer;

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
                new EngineOutcome(players.getFirst(), resultFor(0), scores[0]),
                new EngineOutcome(players.get(1), resultFor(1), scores[1]));
    }

    private GameResultType resultFor(int playerIndex) {
        if (draw) return GameResultType.DRAW;
        return players.get(playerIndex).equals(winner) ? GameResultType.WIN : GameResultType.LOSS;
    }

    private int marker(String action) {
        return switch (action) {
            case "PLACE_S" -> S;
            case "PLACE_O" -> O;
            default -> throw new InvalidGameActionException("SOS only accepts PLACE_S or PLACE_O");
        };
    }

    private int coordinate(Integer value) {
        if (value == null || value < 0 || value >= SIZE) {
            throw new InvalidGameActionException("Coordinate is outside the 6 by 6 SOS board");
        }
        return value;
    }

    private int countCreatedAt(int[][] cells, int row, int column) {
        int total = 0;
        for (int[] axis : AXES) {
            for (int offset = -2; offset <= 0; offset++) {
                int firstRow = row + offset * axis[0];
                int firstColumn = column + offset * axis[1];
                int secondRow = firstRow + axis[0];
                int secondColumn = firstColumn + axis[1];
                int thirdRow = secondRow + axis[0];
                int thirdColumn = secondColumn + axis[1];
                if (inside(firstRow, firstColumn) && inside(thirdRow, thirdColumn)
                        && cells[firstRow][firstColumn] == S
                        && cells[secondRow][secondColumn] == O
                        && cells[thirdRow][thirdColumn] == S) {
                    total++;
                }
            }
        }
        return total;
    }

    private boolean inside(int row, int column) {
        return row >= 0 && row < SIZE && column >= 0 && column < SIZE;
    }

    private void finishByScore() {
        if (scores[0] == scores[1]) draw = true;
        else winner = players.get(scores[0] > scores[1] ? 0 : 1);
    }

    private void replayAndValidate(SosCheckpoint checkpoint) {
        int[][] replayBoard = new int[SIZE][SIZE];
        int[] replayScores = new int[2];
        int replayCurrentPlayer = 0;
        for (int index = 0; index < checkpoint.moveHistory().size(); index++) {
            SosMoveCheckpoint move = checkpoint.moveHistory().get(index);
            if (move.playerIndex() != replayCurrentPlayer
                    || move.marker() < S || move.marker() > O
                    || !inside(move.row(), move.column())
                    || replayBoard[move.row()][move.column()] != EMPTY
                    || move.points() < 0) {
                throw new IllegalArgumentException("SOS checkpoint move history is invalid");
            }
            replayBoard[move.row()][move.column()] = move.marker();
            int points = countCreatedAt(replayBoard, move.row(), move.column());
            if (points != move.points()) {
                throw new IllegalArgumentException("SOS checkpoint score history is invalid");
            }
            replayScores[replayCurrentPlayer] += points;
            if (index < SIZE * SIZE - 1 && points == 0) replayCurrentPlayer = 1 - replayCurrentPlayer;
        }

        int occupied = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < EMPTY || marker > O || marker != replayBoard[row][column]) {
                    throw new IllegalArgumentException("SOS checkpoint board is invalid");
                }
                board[row][column] = marker;
                if (marker != EMPTY) occupied++;
            }
        }

        boolean full = occupied == SIZE * SIZE;
        UUID expectedWinner = replayScores[0] == replayScores[1]
                ? null : players.get(replayScores[0] > replayScores[1] ? 0 : 1);
        SosMoveCheckpoint lastMove = checkpoint.moveHistory().isEmpty()
                ? null : checkpoint.moveHistory().getLast();
        if (occupied != checkpoint.moves()
                || !Arrays.equals(replayScores,
                    new int[]{checkpoint.scores().getFirst(), checkpoint.scores().get(1)})
                || checkpoint.currentPlayer() != replayCurrentPlayer
                || full != (checkpoint.winnerId() != null || checkpoint.draw())
                || checkpoint.draw() && replayScores[0] != replayScores[1]
                || checkpoint.winnerId() != null && !checkpoint.winnerId().equals(expectedWinner)
                || !full && (checkpoint.winnerId() != null || checkpoint.draw())
                || lastMove == null && (checkpoint.lastMoveRow() != null
                    || checkpoint.lastMoveColumn() != null || checkpoint.lastMovePoints() != 0)
                || lastMove != null && (!Integer.valueOf(lastMove.row()).equals(checkpoint.lastMoveRow())
                    || !Integer.valueOf(lastMove.column()).equals(checkpoint.lastMoveColumn())
                    || lastMove.points() != checkpoint.lastMovePoints())) {
            throw new IllegalArgumentException("SOS checkpoint outcome is invalid");
        }
    }
}
