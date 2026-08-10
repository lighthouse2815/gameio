package com.gameio.multiplayer.engine.tictactoe;

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

public final class TicTacToeEngine implements AuthoritativeEngine {
    private static final int SIZE = 3;
    private final List<UUID> players;
    private final int[][] board = new int[SIZE][SIZE];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private UUID winner;
    private boolean draw;

    public TicTacToeEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.get(0).equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Tic Tac Toe requires exactly two distinct players");
        }
        this.players = List.copyOf(playerIds);
    }

    @Override
    public TicTacToeSnapshot snapshot() {
        List<List<String>> rows = new ArrayList<>(SIZE);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(SIZE);
            for (int cell : cells) row.add(cell == 0 ? "" : cell == 1 ? "X" : "O");
            rows.add(List.copyOf(row));
        }
        UUID current = winner == null && !draw ? players.get(currentPlayer) : null;
        return new TicTacToeSnapshot(sequence, List.copyOf(rows), current, winner, draw);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"PLACE_PIECE".equals(input.action())) {
            throw new InvalidGameActionException("Tic Tac Toe only accepts PLACE_PIECE");
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
        board[row][column] = currentPlayer + 1;
        moves++;
        sequence++;
        if (wins(currentPlayer + 1)) {
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
            throw new InvalidGameActionException("Board coordinate is outside the 3 by 3 board");
        }
        return value;
    }

    private boolean wins(int marker) {
        for (int index = 0; index < SIZE; index++) {
            if (board[index][0] == marker && board[index][1] == marker && board[index][2] == marker) return true;
            if (board[0][index] == marker && board[1][index] == marker && board[2][index] == marker) return true;
        }
        return board[0][0] == marker && board[1][1] == marker && board[2][2] == marker
                || board[0][2] == marker && board[1][1] == marker && board[2][0] == marker;
    }

    private List<EngineOutcome> outcomes() {
        return players.stream().map(player -> new EngineOutcome(player,
                draw ? GameResultType.DRAW : player.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                player.equals(winner) ? 1 : 0)).toList();
    }
}
