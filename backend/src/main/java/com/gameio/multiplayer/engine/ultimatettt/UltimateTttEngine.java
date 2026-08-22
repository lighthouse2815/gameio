package com.gameio.multiplayer.engine.ultimatettt;

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

public final class UltimateTttEngine implements AuthoritativeEngine {
    static final int BOARD_SIZE = 9;
    static final int SUB_BOARD_SIZE = 3;
    private static final int SUB_BOARD_DRAW = 3;

    private final List<UUID> players;
    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private final int[][] subBoards = new int[SUB_BOARD_SIZE][SUB_BOARD_SIZE];
    private int currentPlayer;
    private int moves;
    private long sequence;
    private Integer requiredSubBoard;
    private UUID winner;
    private boolean draw;
    private Integer lastMoveRow;
    private Integer lastMoveColumn;

    public UltimateTttEngine(List<UUID> playerIds) {
        validatePlayers(playerIds);
        players = List.copyOf(playerIds);
    }

    UltimateTttEngine(List<UUID> playerIds, UltimateTttCheckpoint checkpoint) {
        this(playerIds);
        Objects.requireNonNull(checkpoint, "Ultimate Tic Tac Toe checkpoint is required");
        if (checkpoint.board().size() != BOARD_SIZE
                || checkpoint.board().stream().anyMatch(row -> row.size() != BOARD_SIZE)
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.moves() < 0 || checkpoint.moves() > BOARD_SIZE * BOARD_SIZE
                || checkpoint.sequence() != checkpoint.moves()
                || checkpoint.requiredSubBoard() != null
                    && (checkpoint.requiredSubBoard() < 0 || checkpoint.requiredSubBoard() >= 9)
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.winnerId() != null && checkpoint.draw()) {
            throw new IllegalArgumentException("Ultimate Tic Tac Toe checkpoint is invalid");
        }

        int occupied = 0;
        int first = 0;
        int second = 0;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int column = 0; column < BOARD_SIZE; column++) {
                int marker = checkpoint.board().get(row).get(column);
                if (marker < 0 || marker > 2) {
                    throw new IllegalArgumentException("Ultimate Tic Tac Toe marker is invalid");
                }
                board[row][column] = marker;
                if (marker != 0) occupied++;
                if (marker == 1) first++;
                if (marker == 2) second++;
            }
        }
        if (occupied != checkpoint.moves() || first < second || first > second + 1) {
            throw new IllegalArgumentException("Ultimate Tic Tac Toe move count is invalid");
        }
        deriveSubBoards();
        currentPlayer = checkpoint.currentPlayer();
        moves = checkpoint.moves();
        sequence = checkpoint.sequence();
        requiredSubBoard = checkpoint.requiredSubBoard();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        lastMoveRow = checkpoint.lastMoveRow();
        lastMoveColumn = checkpoint.lastMoveColumn();
        validateRestoredState(first, second);
    }

    @Override
    public UltimateTttSnapshot snapshot() {
        List<List<String>> boardRows = new ArrayList<>(BOARD_SIZE);
        for (int[] cells : board) {
            List<String> row = new ArrayList<>(BOARD_SIZE);
            for (int cell : cells) row.add(markerName(cell));
            boardRows.add(List.copyOf(row));
        }
        List<List<String>> subBoardRows = new ArrayList<>(SUB_BOARD_SIZE);
        for (int[] cells : subBoards) {
            List<String> row = new ArrayList<>(SUB_BOARD_SIZE);
            for (int cell : cells) row.add(cell == SUB_BOARD_DRAW ? "D" : markerName(cell));
            subBoardRows.add(List.copyOf(row));
        }
        Integer forcedRow = terminal() || requiredSubBoard == null ? null : requiredSubBoard / 3;
        Integer forcedColumn = terminal() || requiredSubBoard == null ? null : requiredSubBoard % 3;
        return new UltimateTttSnapshot(sequence, List.copyOf(boardRows), List.copyOf(subBoardRows),
                forcedRow, forcedColumn, terminal() ? List.of() : legalMoves(),
                terminal() ? null : players.get(currentPlayer), winner, draw, lastMoveRow, lastMoveColumn);
    }

    @Override
    public UltimateTttCheckpoint checkpoint() {
        List<List<Integer>> rows = new ArrayList<>(BOARD_SIZE);
        for (int[] cells : board) {
            List<Integer> row = new ArrayList<>(BOARD_SIZE);
            for (int cell : cells) row.add(cell);
            rows.add(List.copyOf(row));
        }
        return new UltimateTttCheckpoint(UltimateTttCheckpoint.CURRENT_VERSION, sequence, List.copyOf(rows),
                currentPlayer, moves, terminal() ? null : requiredSubBoard, winner, draw,
                lastMoveRow, lastMoveColumn);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"PLACE_MARK".equals(input.action())) {
            throw new InvalidGameActionException("Ultimate Tic Tac Toe only accepts PLACE_MARK");
        }
        if (input.sequence() != null || input.character() != null) {
            throw new InvalidGameActionException("Ultimate Tic Tac Toe accepts board coordinates only");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int row = coordinate(input.row());
        int column = coordinate(input.column());
        int subBoard = subBoardIndex(row, column);
        if (requiredSubBoard != null && requiredSubBoard != subBoard) {
            throw new InvalidGameActionException("Move must be placed in the required sub-board");
        }
        if (subBoards[row / 3][column / 3] != 0) {
            throw new InvalidGameActionException("Sub-board is already closed");
        }
        if (board[row][column] != 0) {
            throw new InvalidGameActionException("Cell is already occupied");
        }

        int marker = currentPlayer + 1;
        board[row][column] = marker;
        moves++;
        sequence++;
        lastMoveRow = row;
        lastMoveColumn = column;
        updateSubBoard(row / 3, column / 3, marker);

        if (winsGrid(subBoards, marker)) {
            winner = userId;
            requiredSubBoard = null;
        } else if (allSubBoardsClosed()) {
            draw = true;
            requiredSubBoard = null;
        } else {
            int target = (row % 3) * 3 + column % 3;
            requiredSubBoard = subBoards[target / 3][target % 3] == 0 ? target : null;
            currentPlayer = 1 - currentPlayer;
        }
        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        if (!terminal()) return List.of();
        return players.stream().map(player -> new EngineOutcome(player,
                draw ? GameResultType.DRAW : player.equals(winner) ? GameResultType.WIN : GameResultType.LOSS,
                player.equals(winner) ? 1 : 0)).toList();
    }

    private List<UltimateTttMove> legalMoves() {
        List<UltimateTttMove> legal = new ArrayList<>();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int column = 0; column < BOARD_SIZE; column++) {
                int subBoard = subBoardIndex(row, column);
                if (board[row][column] == 0
                        && subBoards[row / 3][column / 3] == 0
                        && (requiredSubBoard == null || requiredSubBoard == subBoard)) {
                    legal.add(new UltimateTttMove(row, column));
                }
            }
        }
        return List.copyOf(legal);
    }

    private void deriveSubBoards() {
        for (int subRow = 0; subRow < SUB_BOARD_SIZE; subRow++) {
            for (int subColumn = 0; subColumn < SUB_BOARD_SIZE; subColumn++) {
                boolean firstWins = winsSubBoard(subRow, subColumn, 1);
                boolean secondWins = winsSubBoard(subRow, subColumn, 2);
                if (firstWins && secondWins) {
                    throw new IllegalArgumentException("Ultimate Tic Tac Toe sub-board has two winners");
                }
                if (firstWins) subBoards[subRow][subColumn] = 1;
                else if (secondWins) subBoards[subRow][subColumn] = 2;
                else if (subBoardFull(subRow, subColumn)) subBoards[subRow][subColumn] = SUB_BOARD_DRAW;
            }
        }
    }

    private void updateSubBoard(int subRow, int subColumn, int marker) {
        if (winsSubBoard(subRow, subColumn, marker)) subBoards[subRow][subColumn] = marker;
        else if (subBoardFull(subRow, subColumn)) subBoards[subRow][subColumn] = SUB_BOARD_DRAW;
    }

    private boolean winsSubBoard(int subRow, int subColumn, int marker) {
        int rowStart = subRow * 3;
        int columnStart = subColumn * 3;
        for (int offset = 0; offset < 3; offset++) {
            if (board[rowStart + offset][columnStart] == marker
                    && board[rowStart + offset][columnStart + 1] == marker
                    && board[rowStart + offset][columnStart + 2] == marker) return true;
            if (board[rowStart][columnStart + offset] == marker
                    && board[rowStart + 1][columnStart + offset] == marker
                    && board[rowStart + 2][columnStart + offset] == marker) return true;
        }
        return board[rowStart][columnStart] == marker
                && board[rowStart + 1][columnStart + 1] == marker
                && board[rowStart + 2][columnStart + 2] == marker
                || board[rowStart][columnStart + 2] == marker
                && board[rowStart + 1][columnStart + 1] == marker
                && board[rowStart + 2][columnStart] == marker;
    }

    private boolean subBoardFull(int subRow, int subColumn) {
        for (int row = subRow * 3; row < subRow * 3 + 3; row++) {
            for (int column = subColumn * 3; column < subColumn * 3 + 3; column++) {
                if (board[row][column] == 0) return false;
            }
        }
        return true;
    }

    private boolean allSubBoardsClosed() {
        for (int[] row : subBoards) for (int cell : row) if (cell == 0) return false;
        return true;
    }

    private boolean winsGrid(int[][] grid, int marker) {
        for (int index = 0; index < 3; index++) {
            if (grid[index][0] == marker && grid[index][1] == marker && grid[index][2] == marker) return true;
            if (grid[0][index] == marker && grid[1][index] == marker && grid[2][index] == marker) return true;
        }
        return grid[0][0] == marker && grid[1][1] == marker && grid[2][2] == marker
                || grid[0][2] == marker && grid[1][1] == marker && grid[2][0] == marker;
    }

    private void validateRestoredState(int first, int second) {
        boolean firstWins = winsGrid(subBoards, 1);
        boolean secondWins = winsGrid(subBoards, 2);
        boolean boardClosed = allSubBoardsClosed();
        boolean expectedTerminal = firstWins || secondWins || boardClosed;
        UUID expectedWinner = firstWins ? players.getFirst() : secondWins ? players.get(1) : null;
        boolean expectedDraw = boardClosed && expectedWinner == null;
        int expectedCurrent = moves == 0 ? 0 : expectedTerminal ? (moves - 1) % 2 : moves % 2;

        if (firstWins && secondWins
                || terminal() != expectedTerminal
                || !Objects.equals(winner, expectedWinner)
                || draw != expectedDraw
                || currentPlayer != expectedCurrent
                || winner != null && winner.equals(players.getFirst()) && first != second + 1
                || winner != null && winner.equals(players.get(1)) && first != second
                || moves == 0 && (lastMoveRow != null || lastMoveColumn != null || requiredSubBoard != null)
                || moves > 0 && !lastMoveValid()
                || terminal() && requiredSubBoard != null
                || !terminal() && !Objects.equals(requiredSubBoard, expectedRequiredSubBoard())) {
            throw new IllegalArgumentException("Ultimate Tic Tac Toe checkpoint outcome is invalid");
        }
    }

    private boolean lastMoveValid() {
        if (lastMoveRow == null || lastMoveColumn == null
                || lastMoveRow < 0 || lastMoveRow >= BOARD_SIZE
                || lastMoveColumn < 0 || lastMoveColumn >= BOARD_SIZE) return false;
        int expectedMarker = moves % 2 == 1 ? 1 : 2;
        return board[lastMoveRow][lastMoveColumn] == expectedMarker;
    }

    private Integer expectedRequiredSubBoard() {
        if (moves == 0) return null;
        int target = (lastMoveRow % 3) * 3 + lastMoveColumn % 3;
        return subBoards[target / 3][target % 3] == 0 ? target : null;
    }

    private int coordinate(Integer value) {
        if (value == null || value < 0 || value >= BOARD_SIZE) {
            throw new InvalidGameActionException("Coordinate is outside the Ultimate Tic Tac Toe board");
        }
        return value;
    }

    private int subBoardIndex(int row, int column) {
        return (row / 3) * 3 + column / 3;
    }

    private String markerName(int marker) {
        return marker == 0 ? "" : marker == 1 ? "X" : "O";
    }

    private static void validatePlayers(List<UUID> playerIds) {
        if (playerIds == null || playerIds.size() != 2
                || playerIds.stream().anyMatch(Objects::isNull)
                || playerIds.stream().distinct().count() != 2) {
            throw new IllegalArgumentException("Ultimate Tic Tac Toe requires two distinct players");
        }
    }
}
