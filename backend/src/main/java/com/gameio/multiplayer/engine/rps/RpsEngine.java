package com.gameio.multiplayer.engine.rps;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RpsEngine implements AuthoritativeEngine {
    static final int TARGET_WINS = 3;
    private final List<UUID> players;
    private final int[] scores = new int[2];
    private final int[] choices = new int[2];
    private long sequence;
    private int round = 1;
    private Integer lastRoundNumber;
    private Integer lastFirstChoice;
    private Integer lastSecondChoice;
    private UUID lastRoundWinner;
    private boolean lastRoundDraw;
    private UUID winner;

    public RpsEngine(List<UUID> playerIds) {
        if (playerIds.size() != 2 || playerIds.getFirst().equals(playerIds.get(1))) {
            throw new IllegalArgumentException("Rock Paper Scissors requires exactly two distinct players");
        }
        players = List.copyOf(playerIds);
    }

    RpsEngine(List<UUID> playerIds, RpsCheckpoint checkpoint) {
        this(playerIds);
        if (checkpoint.scores().size() != 2 || checkpoint.choices().size() != 2
                || checkpoint.sequence() < 0 || checkpoint.round() < 1 || checkpoint.round() > 5
                || checkpoint.winnerId() != null && !players.contains(checkpoint.winnerId())
                || checkpoint.lastRoundWinnerId() != null && !players.contains(checkpoint.lastRoundWinnerId())) {
            throw new IllegalArgumentException("Rock Paper Scissors checkpoint is invalid");
        }
        for (int index = 0; index < 2; index++) {
            int score = checkpoint.scores().get(index);
            int choice = checkpoint.choices().get(index);
            if (score < 0 || score > TARGET_WINS || choice < 0 || choice > 3) {
                throw new IllegalArgumentException("Rock Paper Scissors checkpoint values are invalid");
            }
            scores[index] = score;
            choices[index] = choice;
        }
        sequence = checkpoint.sequence();
        round = checkpoint.round();
        lastRoundNumber = checkpoint.lastRoundNumber();
        lastFirstChoice = checkpoint.lastFirstChoice();
        lastSecondChoice = checkpoint.lastSecondChoice();
        lastRoundWinner = checkpoint.lastRoundWinnerId();
        lastRoundDraw = checkpoint.lastRoundDraw();
        winner = checkpoint.winnerId();
        validateCheckpoint();
    }

    @Override
    public RpsSnapshot snapshot() {
        List<RpsPlayerSnapshot> playerStates = List.of(
                new RpsPlayerSnapshot(players.getFirst(), scores[0], choices[0] != 0),
                new RpsPlayerSnapshot(players.get(1), scores[1], choices[1] != 0));
        RpsRoundSnapshot lastRound = lastRoundNumber == null ? null : new RpsRoundSnapshot(
                lastRoundNumber, choiceName(lastFirstChoice), choiceName(lastSecondChoice),
                lastRoundWinner, lastRoundDraw);
        return new RpsSnapshot(sequence, round, TARGET_WINS, playerStates, lastRound, winner, false);
    }

    @Override
    public RpsCheckpoint checkpoint() {
        return new RpsCheckpoint(RpsCheckpoint.CURRENT_VERSION, sequence, round,
                List.of(scores[0], scores[1]), List.of(choices[0], choices[1]), lastRoundNumber,
                lastFirstChoice, lastSecondChoice, lastRoundWinner, lastRoundDraw, winner);
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (!"SELECT_MOVE".equals(input.action())) {
            throw new InvalidGameActionException("Rock Paper Scissors only accepts SELECT_MOVE");
        }
        if (terminal()) throw new InvalidGameActionException("Game is already over");
        int playerIndex = players.indexOf(userId);
        if (playerIndex < 0) throw new InvalidGameActionException("Player does not belong to this match");
        if (choices[playerIndex] != 0) {
            throw new InvalidGameActionException("Choice is already locked for this round");
        }
        int choice = input.column() == null ? -1 : input.column();
        if (choice < 0 || choice > 2) {
            throw new InvalidGameActionException("Choice must be rock, paper, or scissors");
        }
        choices[playerIndex] = choice + 1;
        sequence++;
        if (choices[0] != 0 && choices[1] != 0) resolveRound();
        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public boolean terminal() {
        return winner != null;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        return List.of(
                new EngineOutcome(players.getFirst(), winner.equals(players.getFirst())
                        ? GameResultType.WIN : GameResultType.LOSS, scores[0]),
                new EngineOutcome(players.get(1), winner.equals(players.get(1))
                        ? GameResultType.WIN : GameResultType.LOSS, scores[1]));
    }

    private void resolveRound() {
        lastRoundNumber = round;
        lastFirstChoice = choices[0];
        lastSecondChoice = choices[1];
        int result = (choices[0] - choices[1] + 3) % 3;
        lastRoundDraw = result == 0;
        lastRoundWinner = lastRoundDraw ? null : players.get(result == 1 ? 0 : 1);
        if (!lastRoundDraw) {
            int winnerIndex = players.indexOf(lastRoundWinner);
            scores[winnerIndex]++;
            if (scores[winnerIndex] == TARGET_WINS) winner = lastRoundWinner;
        }
        choices[0] = 0;
        choices[1] = 0;
        if (winner == null) round++;
    }

    private String choiceName(Integer choice) {
        if (choice == null) return null;
        return switch (choice) {
            case 1 -> "ROCK";
            case 2 -> "PAPER";
            case 3 -> "SCISSORS";
            default -> throw new IllegalArgumentException("Rock Paper Scissors choice is invalid");
        };
    }

    private void validateCheckpoint() {
        int submitted = (choices[0] == 0 ? 0 : 1) + (choices[1] == 0 ? 0 : 1);
        boolean lastRoundMissing = lastRoundNumber == null || lastFirstChoice == null || lastSecondChoice == null;
        if (choices[0] != 0 && choices[1] != 0
                || winner == null && (scores[0] >= TARGET_WINS || scores[1] >= TARGET_WINS)
                || winner != null && scores[players.indexOf(winner)] != TARGET_WINS
                || scores[0] == TARGET_WINS && scores[1] == TARGET_WINS
                || sequence != (winner == null ? 2L * (round - 1) + submitted : 2L * round)
                || (round == 1 && sequence == submitted) != lastRoundMissing
                || !lastRoundMissing && (lastRoundNumber < 1 || lastRoundNumber > round
                    || lastFirstChoice < 1 || lastFirstChoice > 3
                    || lastSecondChoice < 1 || lastSecondChoice > 3)
                || lastRoundMissing && (lastRoundWinner != null || lastRoundDraw)
                || !lastRoundMissing && lastRoundDraw != lastFirstChoice.equals(lastSecondChoice)
                || !lastRoundMissing && !lastRoundDraw && lastRoundWinner == null) {
            throw new IllegalArgumentException("Rock Paper Scissors checkpoint state is invalid");
        }
    }
}
