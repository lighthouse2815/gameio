package com.gameio.multiplayer.engine.caro;

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

public final class CaroEngine implements AuthoritativeEngine {
    public static final int SIZE = 15;
    private static final int WIN_LENGTH = 5;
    private final List<UUID> players;
    private final int[][] board = new int[SIZE][SIZE];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private boolean draw;

    public CaroEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.get(0).equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Caro requires exactly two distinct players");
        }
        this.players = List.copyOf(playerIds);
    }

    CaroEngine(List<UUID> playerIds, CaroCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.board().size() != SIZE || checkpoint.board().stream().anyMatch(row -> row.size() != SIZE)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() >= players.size()
                || checkpoint.sequence() < 0 || checkpoint.moves() < 0 || checkpoint.moves() > SIZE * SIZE
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())) {
            throw new IllegalArgumentException("Caro checkpoint is invalid");
        }
        int occupied = 0;
        int firstMarkers = 0;
        int secondMarkers = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < 0 || marker > 2) throw new IllegalArgumentException("Caro marker is invalid");
                board[row][column] = marker;
                if (marker != 0) occupied++;
                if (marker == 1) firstMarkers++;
                if (marker == 2) secondMarkers++;
            }
        }
        if (occupied != checkpoint.moves() || checkpoint.sequence() != checkpoint.moves()
                || firstMarkers < secondMarkers || firstMarkers > secondMarkers + 1
                || checkpoint.draw() && checkpoint.winnerId() != null) {
            throw new IllegalArgumentException("Caro move count is invalid");
        }
        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        validateOutcome(firstMarkers, secondMarkers);
    }

    @Override
    public CaroSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell == 0 ? "" : cell == 1 ? "X" : "O");
            rows.add(List.copyOf(row));
        }
        UUID current = winner == null && !draw ? players.get(currentPlayer) : null;
        return new CaroSnapshot(sequence, SIZE, List.copyOf(rows), current, winner, draw);
    }

    @Override
    public CaroCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new CaroCheckpoint(CaroCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, winner, draw);
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"PLACE_PIECE".equals(input.action())) {
            throw new InvalidGameActionException("Caro only accepts PLACE_PIECE");
        }
        if (winner != null || draw) {
            throw new InvalidGameActionException("Game is already over");
        }
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int row = requireCoordinate(input.row());
        int column = requireCoordinate(input.column());
        if (board[row][column] != 0) {
            throw new InvalidGameActionException("Board cell is already occupied");
        }
        int marker = currentPlayer + 1;
        board[row][column] = marker;
        moves++;
        sequence++;
        if (wins(row, column, marker)) {
            winner = userId;
        } else if (moves == SIZE * SIZE) {
            draw = true;
        } else {
            currentPlayer = 1 - currentPlayer;
        }
        boolean terminal = winner != null || draw;
        return new EngineUpdate(true, snapshot(), terminal, terminal ? outcomes() : List.of());
    }

    private int requireCoordinate(Integer value) {
        if (value == null || value < 0 || value >= SIZE) {
            throw new InvalidGameActionException("Board coordinate is outside the 15 by 15 board");
        }
        return value;
    }

    private boolean wins(int row, int column, int marker) {
        return lineLength(row, column, marker, 1, 0) >= WIN_LENGTH
                || lineLength(row, column, marker, 0, 1) >= WIN_LENGTH
                || lineLength(row, column, marker, 1, 1) >= WIN_LENGTH
                || lineLength(row, column, marker, 1, -1) >= WIN_LENGTH;
    }

    private int lineLength(int row, int column, int marker, int rowStep, int columnStep) {
        return 1 + count(row, column, marker, rowStep, columnStep)
                + count(row, column, marker, -rowStep, -columnStep);
    }

    private int count(int row, int column, int marker, int rowStep, int columnStep) {
        int total = 0;
        int nextRow = row + rowStep;
        int nextColumn = column + columnStep;
        while (nextRow >= 0 && nextRow < SIZE && nextColumn >= 0 && nextColumn < SIZE
                && board[nextRow][nextColumn] == marker) {
            total++;
            nextRow += rowStep;
            nextColumn += columnStep;
        }
        return total;
    }

    private void validateOutcome(int firstMarkers, int secondMarkers) {
        boolean firstWins = hasWinningLine(1);
        boolean secondWins = hasWinningLine(2);
        if (firstWins && secondWins
                || winner == null && (firstWins || secondWins)
                || winner != null && !(winner.equals(players.getFirst()) ? firstWins : secondWins)
                || winner != null && winner.equals(players.getFirst()) && firstMarkers != secondMarkers + 1
                || winner != null && winner.equals(players.get(1)) && firstMarkers != secondMarkers
                || draw && (moves != SIZE * SIZE || firstWins || secondWins)
                || !terminal() && currentPlayer != moves % players.size()) {
            throw new IllegalArgumentException("Caro checkpoint outcome is invalid");
        }
    }

    private boolean hasWinningLine(int marker) {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (board[row][column] == marker && wins(row, column, marker)) return true;
            }
        }
        return false;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        return players.stream().map(player -> new EngineOutcome(player,
                draw ? GameResultType.DRAW : player.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                player.equals(winner) ? 1 : 0)).toList();
    }
}
