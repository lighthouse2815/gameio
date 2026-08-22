package com.gameio.multiplayer.engine.mancala;

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

public final class MancalaEngine implements AuthoritativeEngine {
    static final int RING_SIZE = 14;
    static final int PITS_PER_PLAYER = 6;
    static final int INITIAL_STONES_PER_PIT = 4;
    static final int TOTAL_STONES = PITS_PER_PLAYER * 2 * INITIAL_STONES_PER_PIT;
    private static final int FIRST_STORE = 6;
    private static final int SECOND_STORE = 13;

    private final List<UUID> players;
    private final int[] pits = new int[RING_SIZE];
    private int currentPlayer;
    private long sequence;
    private Integer lastPit;
    private UUID winner;
    private boolean draw;

    public MancalaEngine(List<UUID> playerIds) {
        validatePlayers(playerIds);
        players = List.copyOf(playerIds);
        for (int position = 0; position < RING_SIZE; position++) {
            if (position != FIRST_STORE && position != SECOND_STORE) pits[position] = INITIAL_STONES_PER_PIT;
        }
    }

    MancalaEngine(List<UUID> playerIds, MancalaCheckpoint checkpoint) {
        validatePlayers(playerIds);
        players = List.copyOf(playerIds);
        Objects.requireNonNull(checkpoint, "Mancala checkpoint is required");
        if (checkpoint.pits().size() != RING_SIZE
                || checkpoint.currentPlayer() < 0 || checkpoint.currentPlayer() > 1
                || checkpoint.sequence() < 0
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.winnerId() != null && checkpoint.draw()) {
            throw new IllegalArgumentException("Mancala checkpoint is invalid");
        }
        int stones = 0;
        for (int position = 0; position < RING_SIZE; position++) {
            Integer value = checkpoint.pits().get(position);
            if (value == null || value < 0 || value > TOTAL_STONES) {
                throw new IllegalArgumentException("Mancala pit value is invalid");
            }
            pits[position] = value;
            stones += value;
        }
        if (stones != TOTAL_STONES) {
            throw new IllegalArgumentException("Mancala checkpoint must preserve all stones");
        }
        currentPlayer = checkpoint.currentPlayer();
        sequence = checkpoint.sequence();
        lastPit = checkpoint.lastPit();
        winner = checkpoint.winnerId();
        draw = checkpoint.draw();
        validateRestoredState();
    }

    @Override
    public MancalaSnapshot snapshot() {
        List<Integer> ring = new ArrayList<>(RING_SIZE);
        for (int pit : pits) ring.add(pit);
        return new MancalaSnapshot(sequence, List.copyOf(ring),
                List.of(pits[FIRST_STORE], pits[SECOND_STORE]),
                terminal() ? List.of() : legalPits(), lastPit,
                terminal() ? null : players.get(currentPlayer), winner, draw);
    }

    @Override
    public MancalaCheckpoint checkpoint() {
        List<Integer> ring = new ArrayList<>(RING_SIZE);
        for (int pit : pits) ring.add(pit);
        return new MancalaCheckpoint(MancalaCheckpoint.CURRENT_VERSION, sequence,
                List.copyOf(ring), currentPlayer, lastPit, winner, draw);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"SOW_PIT".equals(input.action())) {
            throw new InvalidGameActionException("Mancala only accepts SOW_PIT");
        }
        if (input.row() != null || input.sequence() != null || input.character() != null) {
            throw new InvalidGameActionException("Mancala accepts one relative pit column only");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        if (!players.get(currentPlayer).equals(userId)) {
            throw new InvalidGameActionException("It is not this player's turn");
        }
        int relativePit = relativePit(input.column());
        int start = pitPosition(currentPlayer, relativePit);
        int stones = pits[start];
        if (stones == 0) throw new InvalidGameActionException("Selected Mancala pit is empty");

        pits[start] = 0;
        int position = start;
        while (stones > 0) {
            position = (position + 1) % RING_SIZE;
            if (position == opponentStore(currentPlayer)) continue;
            pits[position]++;
            stones--;
        }
        lastPit = position;
        captureIfEligible(currentPlayer, position);
        sequence++;

        if (sideEmpty(0) || sideEmpty(1)) finishAndSweep();
        else if (position != ownStore(currentPlayer)) currentPlayer = 1 - currentPlayer;

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
                        : winner.equals(players.getFirst()) ? GameResultType.WIN : GameResultType.LOSS,
                        pits[FIRST_STORE]),
                new EngineOutcome(players.get(1), draw ? GameResultType.DRAW
                        : winner.equals(players.get(1)) ? GameResultType.WIN : GameResultType.LOSS,
                        pits[SECOND_STORE]));
    }

    private List<Integer> legalPits() {
        List<Integer> legal = new ArrayList<>(PITS_PER_PLAYER);
        for (int relative = 0; relative < PITS_PER_PLAYER; relative++) {
            if (pits[pitPosition(currentPlayer, relative)] > 0) legal.add(relative);
        }
        return List.copyOf(legal);
    }

    private void captureIfEligible(int player, int position) {
        if (!isOwnPit(player, position) || pits[position] != 1) return;
        int opposite = 12 - position;
        if (pits[opposite] == 0) return;
        pits[ownStore(player)] += pits[position] + pits[opposite];
        pits[position] = 0;
        pits[opposite] = 0;
    }

    private void finishAndSweep() {
        for (int player = 0; player < 2; player++) {
            int store = ownStore(player);
            for (int relative = 0; relative < PITS_PER_PLAYER; relative++) {
                int position = pitPosition(player, relative);
                pits[store] += pits[position];
                pits[position] = 0;
            }
        }
        if (pits[FIRST_STORE] == pits[SECOND_STORE]) draw = true;
        else winner = players.get(pits[FIRST_STORE] > pits[SECOND_STORE] ? 0 : 1);
    }

    private boolean sideEmpty(int player) {
        for (int relative = 0; relative < PITS_PER_PLAYER; relative++) {
            if (pits[pitPosition(player, relative)] > 0) return false;
        }
        return true;
    }

    private void validateRestoredState() {
        boolean firstEmpty = sideEmpty(0);
        boolean secondEmpty = sideEmpty(1);
        boolean expectedTerminal = firstEmpty && secondEmpty;
        UUID expectedWinner = expectedTerminal && pits[FIRST_STORE] != pits[SECOND_STORE]
                ? players.get(pits[FIRST_STORE] > pits[SECOND_STORE] ? 0 : 1) : null;
        boolean expectedDraw = expectedTerminal && pits[FIRST_STORE] == pits[SECOND_STORE];
        boolean initial = sequence == 0;

        if (firstEmpty != secondEmpty
                || terminal() != expectedTerminal
                || !Objects.equals(winner, expectedWinner)
                || draw != expectedDraw
                || initial && !isInitialPosition()
                || initial && (currentPlayer != 0 || lastPit != null)
                || !initial && (lastPit == null || lastPit < 0 || lastPit >= RING_SIZE)
                || lastPit != null && lastPit == opponentStore(currentPlayer)) {
            throw new IllegalArgumentException("Mancala checkpoint outcome is invalid");
        }
    }

    private boolean isInitialPosition() {
        if (pits[FIRST_STORE] != 0 || pits[SECOND_STORE] != 0) return false;
        for (int position = 0; position < RING_SIZE; position++) {
            if (position != FIRST_STORE && position != SECOND_STORE
                    && pits[position] != INITIAL_STONES_PER_PIT) return false;
        }
        return true;
    }

    private int relativePit(Integer value) {
        if (value == null || value < 0 || value >= PITS_PER_PLAYER) {
            throw new InvalidGameActionException("Mancala pit must be between zero and five");
        }
        return value;
    }

    private int pitPosition(int player, int relativePit) {
        return player == 0 ? relativePit : 7 + relativePit;
    }

    private int ownStore(int player) {
        return player == 0 ? FIRST_STORE : SECOND_STORE;
    }

    private int opponentStore(int player) {
        return player == 0 ? SECOND_STORE : FIRST_STORE;
    }

    private boolean isOwnPit(int player, int position) {
        return player == 0 ? position >= 0 && position < FIRST_STORE
                : position > FIRST_STORE && position < SECOND_STORE;
    }

    private static void validatePlayers(List<UUID> playerIds) {
        if (playerIds == null || playerIds.size() != 2
                || playerIds.stream().anyMatch(Objects::isNull)
                || playerIds.stream().distinct().count() != 2) {
            throw new IllegalArgumentException("Mancala requires two distinct players");
        }
    }
}
