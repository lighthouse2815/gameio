package com.gameio.multiplayer.engine.connectfour;

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

public final class ConnectFourEngine implements AuthoritativeEngine {
    static final int ROWS = 6;
    static final int COLUMNS = 7;
    private final List<UUID> players;
    private final int[][] board = new int[ROWS][COLUMNS];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private boolean draw;
    private Integer lastMoveRow;
    private Integer lastMoveColumn;

    public ConnectFourEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.getFirst().equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Connect Four requires exactly two distinct players");
        }
        players = List.copyOf(playerIds);
    }

    ConnectFourEngine(List<UUID> playerIds, ConnectFourCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.board().size() != ROWS
                || checkpoint.board().stream().anyMatch(row -> row.size() != COLUMNS)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.moves() < 0 || checkpoint.moves() > ROWS * COLUMNS
                || checkpoint.sequence() != checkpoint.moves()
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.draw() && checkpoint.winnerId() != null) {
            throw new IllegalArgumentException("Connect Four checkpoint is invalid");
        }
        int occupied = 0;
        int first = 0;
        int second = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < 0 || marker > 2) {
                    throw new IllegalArgumentException("Connect Four marker is invalid");
                }
                board[row][column] = marker;
                if (marker != 0) occupied++;
                if (marker == 1) first++;
                if (marker == 2) second++;
                if (row < ROWS - 1 && marker != 0 && checkpoint.board().get(row + 1).get(column) == 0) {
                    throw new IllegalArgumentException("Connect Four checkpoint violates gravity");
                }
            }
        }
        if (occupied != checkpoint.moves() || first < second || first > second + 1) {
            throw new IllegalArgumentException("Connect Four move count is invalid");
        }
        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        lastMoveRow = checkpoint.lastMoveRow();
        lastMoveColumn = checkpoint.lastMoveColumn();
        validateOutcome(first, second);
    }

    @Override
    public ConnectFourSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(ROWS);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(COLUMNS);
            for (int cell : cells) row.add(cell == 0 ? "" : cell == 1 ? "R" : "Y");
            rows.add(List.copyOf(row));
        }
        UUID current = terminal() ? null : players.get(currentPlayer);
        return new ConnectFourSnapshot(sequence, List.copyOf(rows), current, winner, draw,
                lastMoveRow, lastMoveColumn);
    }

    @Override
    public ConnectFourCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(ROWS);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(COLUMNS);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new ConnectFourCheckpoint(ConnectFourCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, winner, draw, lastMoveRow, lastMoveColumn);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"DROP_DISC".equals(input.action())) {
            throw new InvalidGameActionException("Connect Four only accepts DROP_DISC");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int column = input.column() == null ? -1 : input.column();
        if (column < 0 || column >= COLUMNS) {
            throw new InvalidGameActionException("Column is outside the Connect Four board");
        }
        int row = landingRow(column);
        if (row < 0) throw new InvalidGameActionException("Column is full");
        board[row][column] = currentPlayer + 1;
        lastMoveRow = row;
        lastMoveColumn = column;
        moves++;
        sequence++;
        if (winsFrom(row, column, currentPlayer + 1)) winner = userId;
        else if (moves == ROWS * COLUMNS) draw = true;
        else currentPlayer = 1 - currentPlayer;
        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        return players.stream().map(player -> new EngineOutcome(player,
                draw ? GameResultType.DRAW : player.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                player.equals(winner) ? 1 : 0)).toList();
    }

    private int landingRow(int column) {
        for (int row = ROWS - 1; row >= 0; row--) if (board[row][column] == 0) return row;
        return -1;
    }

    private boolean winsFrom(int row, int column, int marker) {
        return lineLength(row, column, 1, 0, marker) >= 4
                || lineLength(row, column, 0, 1, marker) >= 4
                || lineLength(row, column, 1, 1, marker) >= 4
                || lineLength(row, column, 1, -1, marker) >= 4;
    }

    private int lineLength(int row, int column, int rowStep, int columnStep, int marker) {
        return 1 + count(row, column, rowStep, columnStep, marker)
                + count(row, column, -rowStep, -columnStep, marker);
    }

    private int count(int row, int column, int rowStep, int columnStep, int marker) {
        int total = 0;
        for (int r = row + rowStep, c = column + columnStep;
             r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && board[r][c] == marker;
             r += rowStep, c += columnStep) total++;
        return total;
    }

    private boolean anyWin(int marker) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (board[row][column] == marker && winsFrom(row, column, marker)) return true;
            }
        }
        return false;
    }

    private void validateOutcome(int first, int second) {
        boolean firstWins = anyWin(1);
        boolean secondWins = anyWin(2);
        if (firstWins && secondWins
                || winner == null && (firstWins || secondWins)
                || winner != null && !anyWin(players.indexOf(winner) + 1)
                || winner != null && winner.equals(players.getFirst()) && first != second + 1
                || winner != null && winner.equals(players.get(1)) && first != second
                || draw && (moves != ROWS * COLUMNS || firstWins || secondWins)
                || !terminal() && currentPlayer != moves % 2
                || moves == 0 && (lastMoveRow != null || lastMoveColumn != null)
                || moves > 0 && (lastMoveRow == null || lastMoveColumn == null
                    || lastMoveRow < 0 || lastMoveRow >= ROWS || lastMoveColumn < 0 || lastMoveColumn >= COLUMNS
                    || board[lastMoveRow][lastMoveColumn] == 0)) {
            throw new IllegalArgumentException("Connect Four checkpoint outcome is invalid");
        }
    }
}
