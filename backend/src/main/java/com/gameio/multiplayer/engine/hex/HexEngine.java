package com.gameio.multiplayer.engine.hex;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HexEngine implements AuthoritativeEngine {
    static final int SIZE = 9;
    private static final int[][] NEIGHBORS = {
            {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}
    };

    private final List<UUID> players;
    private final int[][] board = new int[SIZE][SIZE];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private Integer lastMoveRow;
    private Integer lastMoveColumn;

    public HexEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.getFirst().equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Hex requires exactly two distinct players");
        }
        players = List.copyOf(playerIds);
    }

    HexEngine(List<UUID> playerIds, HexCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.board().size() != SIZE
                || checkpoint.board().stream().anyMatch(row -> row.size() != SIZE)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.moves() < 0 || checkpoint.moves() > SIZE * SIZE
                || checkpoint.sequence() != checkpoint.moves()
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())) {
            throw new IllegalArgumentException("Hex checkpoint is invalid");
        }

        int first = 0;
        int second = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < 0 || marker > 2) {
                    throw new IllegalArgumentException("Hex marker is invalid");
                }
                board[row][column] = marker;
                if (marker == 1) first++;
                if (marker == 2) second++;
            }
        }
        if (first + second != checkpoint.moves() || first < second || first > second + 1) {
            throw new IllegalArgumentException("Hex move count is invalid");
        }

        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        winner = checkpoint.winnerId();
        lastMoveRow = checkpoint.lastMoveRow();
        lastMoveColumn = checkpoint.lastMoveColumn();
        validateOutcome(first, second);
    }

    @Override
    public HexSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell == 0 ? "" : cell == 1 ? "R" : "B");
            rows.add(List.copyOf(row));
        }
        return new HexSnapshot(sequence, List.copyOf(rows), terminal() ? null : players.get(currentPlayer),
                winner, lastMoveRow, lastMoveColumn);
    }

    @Override
    public HexCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new HexCheckpoint(HexCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, winner, lastMoveRow, lastMoveColumn);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"PLACE_STONE".equals(input.action())) {
            throw new InvalidGameActionException("Hex only accepts PLACE_STONE");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.contains(userId)) {
            throw new InvalidGameActionException("Player does not belong to this match");
        }
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }

        int row = coordinate(input.row());
        int column = coordinate(input.column());
        if (board[row][column] != 0) {
            throw new InvalidGameActionException("Board cell is already occupied");
        }

        int marker = currentPlayer + 1;
        board[row][column] = marker;
        lastMoveRow = row;
        lastMoveColumn = column;
        moves++;
        sequence++;
        if (hasConnection(marker)) winner = userId;
        else currentPlayer = 1 - currentPlayer;

        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        if (!terminal()) return List.of();
        return players.stream().map(player -> new EngineOutcome(player,
                player.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                player.equals(winner) ? 1 : 0)).toList();
    }

    private int coordinate(Integer value) {
        if (value == null || value < 0 || value >= SIZE) {
            throw new InvalidGameActionException("Coordinate is outside the 9 by 9 Hex board");
        }
        return value;
    }

    private boolean hasConnection(int marker) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        if (marker == 1) {
            for (int column = 0; column < SIZE; column++) {
                if (board[0][column] == marker) {
                    visited[0][column] = true;
                    pending.add(column);
                }
            }
        } else {
            for (int row = 0; row < SIZE; row++) {
                if (board[row][0] == marker) {
                    visited[row][0] = true;
                    pending.add(row * SIZE);
                }
            }
        }

        while (!pending.isEmpty()) {
            int cell = pending.removeFirst();
            int row = cell / SIZE;
            int column = cell % SIZE;
            if ((marker == 1 && row == SIZE - 1) || (marker == 2 && column == SIZE - 1)) return true;
            for (int[] neighbor : NEIGHBORS) {
                int nextRow = row + neighbor[0];
                int nextColumn = column + neighbor[1];
                if (inside(nextRow, nextColumn)
                        && !visited[nextRow][nextColumn]
                        && board[nextRow][nextColumn] == marker) {
                    visited[nextRow][nextColumn] = true;
                    pending.add(nextRow * SIZE + nextColumn);
                }
            }
        }
        return false;
    }

    private boolean inside(int row, int column) {
        return row >= 0 && row < SIZE && column >= 0 && column < SIZE;
    }

    private void validateOutcome(int first, int second) {
        boolean firstWins = hasConnection(1);
        boolean secondWins = hasConnection(2);
        int lastMarker = moves == 0 || lastMoveRow == null || lastMoveColumn == null
                || !inside(lastMoveRow, lastMoveColumn) ? 0 : board[lastMoveRow][lastMoveColumn];
        if (firstWins && secondWins
                || winner == null && (firstWins || secondWins)
                || winner != null && !hasConnection(players.indexOf(winner) + 1)
                || winner != null && winner.equals(players.getFirst()) && first != second + 1
                || winner != null && winner.equals(players.get(1)) && first != second
                || moves == SIZE * SIZE && winner == null
                || !terminal() && currentPlayer != moves % 2
                || moves == 0 && (lastMoveRow != null || lastMoveColumn != null)
                || moves > 0 && (lastMoveRow == null || lastMoveColumn == null
                    || !inside(lastMoveRow, lastMoveColumn) || lastMarker == 0)
                || !terminal() && moves > 0 && lastMarker != 2 - currentPlayer
                || terminal() && lastMarker != players.indexOf(winner) + 1) {
            throw new IllegalArgumentException("Hex checkpoint outcome is invalid");
        }
    }
}
