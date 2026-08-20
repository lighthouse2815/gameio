package com.gameio.multiplayer.engine.reversi;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ReversiEngine implements AuthoritativeEngine {
    static final int SIZE = 8;
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
            {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };
    private final List<UUID> players;
    private final int[][] board = new int[SIZE][SIZE];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private boolean draw;
    private Integer lastMoveRow;
    private Integer lastMoveColumn;

    public ReversiEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.getFirst().equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Reversi requires exactly two distinct players");
        }
        players = List.copyOf(playerIds);
        board[3][3] = 2;
        board[3][4] = 1;
        board[4][3] = 1;
        board[4][4] = 2;
    }

    ReversiEngine(List<UUID> playerIds, ReversiCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.board().size() != SIZE
                || checkpoint.board().stream().anyMatch(row -> row.size() != SIZE)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.moves() < 0 || checkpoint.moves() > SIZE * SIZE - 4
                || checkpoint.sequence() != checkpoint.moves()
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.draw() && checkpoint.winnerId() != null) {
            throw new IllegalArgumentException("Reversi checkpoint is invalid");
        }
        int occupied = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < 0 || marker > 2) throw new IllegalArgumentException("Reversi marker is invalid");
                board[row][column] = marker;
                if (marker != 0) occupied++;
            }
        }
        if (occupied != checkpoint.moves() + 4) {
            throw new IllegalArgumentException("Reversi move count is invalid");
        }
        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        lastMoveRow = checkpoint.lastMoveRow();
        lastMoveColumn = checkpoint.lastMoveColumn();
        validateOutcome();
    }

    @Override
    public ReversiSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(SIZE);
        int black = 0;
        int white = 0;
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(SIZE);
            for (int cell : cells) {
                row.add(cell == 0 ? "" : cell == 1 ? "B" : "W");
                if (cell == 1) black++;
                if (cell == 2) white++;
            }
            rows.add(List.copyOf(row));
        }
        return new ReversiSnapshot(sequence, List.copyOf(rows), terminal() ? null : players.get(currentPlayer),
                winner, draw, black, white, terminal() ? List.of() : legalMoves(currentPlayer + 1),
                lastMoveRow, lastMoveColumn);
    }

    @Override
    public ReversiCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new ReversiCheckpoint(ReversiCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, winner, draw, lastMoveRow, lastMoveColumn);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"PLACE_DISC".equals(input.action())) {
            throw new InvalidGameActionException("Reversi only accepts PLACE_DISC");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int row = coordinate(input.row());
        int column = coordinate(input.column());
        List<ReversiMove> flips = flipsFor(row, column, currentPlayer + 1);
        if (flips.isEmpty()) throw new InvalidGameActionException("Move captures no rival discs");
        board[row][column] = currentPlayer + 1;
        flips.forEach(move -> board[move.row()][move.column()] = currentPlayer + 1);
        lastMoveRow = row;
        lastMoveColumn = column;
        moves++;
        sequence++;

        int opponent = 1 - currentPlayer;
        if (!legalMoves(opponent + 1).isEmpty()) currentPlayer = opponent;
        else if (legalMoves(currentPlayer + 1).isEmpty()) finishByDiscCount();

        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        int black = count(1);
        int white = count(2);
        return List.of(
                new EngineOutcome(players.getFirst(), draw ? GameResultType.DRAW
                        : winner.equals(players.getFirst()) ? GameResultType.WIN : GameResultType.LOSS, black),
                new EngineOutcome(players.get(1), draw ? GameResultType.DRAW
                        : winner.equals(players.get(1)) ? GameResultType.WIN : GameResultType.LOSS, white));
    }

    private int coordinate(Integer value) {
        if (value == null || value < 0 || value >= SIZE) {
            throw new InvalidGameActionException("Coordinate is outside the Reversi board");
        }
        return value;
    }

    private List<ReversiMove> legalMoves(int marker) {
        List<ReversiMove> moves = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (!flipsFor(row, column, marker).isEmpty()) moves.add(new ReversiMove(row, column));
            }
        }
        return List.copyOf(moves);
    }

    private List<ReversiMove> flipsFor(int row, int column, int marker) {
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE || board[row][column] != 0) {
            return List.of();
        }
        List<ReversiMove> flips = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            List<ReversiMove> line = new ArrayList<>();
            int r = row + direction[0];
            int c = column + direction[1];
            while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == 3 - marker) {
                line.add(new ReversiMove(r, c));
                r += direction[0];
                c += direction[1];
            }
            if (!line.isEmpty() && r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == marker) {
                flips.addAll(line);
            }
        }
        return List.copyOf(flips);
    }

    private int count(int marker) {
        int total = 0;
        for (int[] row : board) for (int cell : row) if (cell == marker) total++;
        return total;
    }

    private void finishByDiscCount() {
        int black = count(1);
        int white = count(2);
        if (black == white) draw = true;
        else winner = players.get(black > white ? 0 : 1);
    }

    private void validateOutcome() {
        boolean noMovesRemain = legalMoves(1).isEmpty() && legalMoves(2).isEmpty();
        int black = count(1);
        int white = count(2);
        UUID expectedWinner = black == white ? null : players.get(black > white ? 0 : 1);
        if (terminal() != noMovesRemain
                || draw && black != white
                || winner != null && !winner.equals(expectedWinner)
                || !terminal() && legalMoves(currentPlayer + 1).isEmpty()
                || moves == 0 && (lastMoveRow != null || lastMoveColumn != null)
                || moves > 0 && (lastMoveRow == null || lastMoveColumn == null
                    || lastMoveRow < 0 || lastMoveRow >= SIZE || lastMoveColumn < 0 || lastMoveColumn >= SIZE
                    || board[lastMoveRow][lastMoveColumn] == 0)) {
            throw new IllegalArgumentException("Reversi checkpoint outcome is invalid");
        }
    }
}
