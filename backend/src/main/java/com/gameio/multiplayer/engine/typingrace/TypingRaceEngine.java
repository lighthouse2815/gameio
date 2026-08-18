package com.gameio.multiplayer.engine.typingrace;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.GameResultType;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TypingRaceEngine implements AuthoritativeEngine {
    public static final Duration COUNTDOWN = Duration.ofSeconds(3);
    public static final Duration RACE_DURATION = Duration.ofSeconds(90);

    private final TypingPassage passage;
    private final int passageLength;
    private final Map<UUID, MutablePlayer> players = new LinkedHashMap<>();
    private final Instant startsAt;
    private final Instant deadline;
    private long sequence;
    private Instant endedAt;
    private UUID winnerId;
    private boolean draw;

    public TypingRaceEngine(List<UUID> playerIds, TypingPassage passage, Instant createdAt) {
        validatePlayers(playerIds);
        this.passage = passage;
        this.passageLength = passage.text().codePointCount(0, passage.text().length());
        this.startsAt = createdAt.plus(COUNTDOWN);
        this.deadline = startsAt.plus(RACE_DURATION);
        playerIds.forEach(playerId -> players.put(playerId, new MutablePlayer(playerId)));
    }

    TypingRaceEngine(List<UUID> playerIds, TypingRaceCheckpoint checkpoint) {
        validatePlayers(playerIds);
        if (checkpoint == null || checkpoint.sequence() < 0
                || checkpoint.passageId() == null || checkpoint.passageId().isBlank()
                || checkpoint.passage() == null || checkpoint.passage().isBlank()
                || checkpoint.startsAt() == null || checkpoint.deadline() == null
                || !checkpoint.startsAt().isBefore(checkpoint.deadline())
                || !Duration.between(checkpoint.startsAt(), checkpoint.deadline()).equals(RACE_DURATION)
                || checkpoint.players().size() != playerIds.size()
                || checkpoint.winnerId() != null && !playerIds.contains(checkpoint.winnerId())
                || checkpoint.winnerId() != null && checkpoint.draw()
                || (checkpoint.winnerId() != null || checkpoint.draw()) != (checkpoint.endedAt() != null)) {
            throw new IllegalArgumentException("Typing Race checkpoint is invalid");
        }
        this.passage = new TypingPassage(checkpoint.passageId(), checkpoint.passage());
        this.passageLength = passage.text().codePointCount(0, passage.text().length());
        this.startsAt = checkpoint.startsAt();
        this.deadline = checkpoint.deadline();
        this.sequence = checkpoint.sequence();
        this.endedAt = checkpoint.endedAt();
        this.winnerId = checkpoint.winnerId();
        this.draw = checkpoint.draw();

        for (int index = 0; index < playerIds.size(); index++) {
            UUID expectedId = playerIds.get(index);
            TypingRaceCheckpoint.PlayerState state = checkpoint.players().get(index);
            if (!expectedId.equals(state.userId())) {
                throw new IllegalArgumentException("Typing Race checkpoint player order is invalid");
            }
            MutablePlayer player = restorePlayer(state);
            players.put(expectedId, player);
        }
        validateTerminalState();
    }

    @Override
    public boolean requiresServerTick() {
        return true;
    }

    @Override
    public TypingRaceSnapshot snapshot() {
        Instant metricAt = endedAt;
        return new TypingRaceSnapshot(sequence, passage.id(), passage.text(), startsAt, deadline,
                players.values().stream().map(player -> player.snapshot(metricAt)).toList(),
                winnerId, draw, terminal());
    }

    @Override
    public TypingRaceCheckpoint checkpoint() {
        return new TypingRaceCheckpoint(TypingRaceCheckpoint.CURRENT_VERSION, sequence,
                passage.id(), passage.text(), startsAt, deadline, endedAt, winnerId, draw,
                players.values().stream().map(MutablePlayer::checkpoint).toList());
    }

    @Override
    public EngineUpdate input(UUID userId, GameInput input, Instant now) {
        if (terminal()) {
            throw new InvalidGameActionException("Typing race is already over");
        }
        MutablePlayer player = players.get(userId);
        if (player == null) {
            throw new InvalidGameActionException("Player is not part of this typing race");
        }
        if (!now.isBefore(deadline)) {
            settleTimeout();
            sequence++;
            return new EngineUpdate(true, snapshot(), true, outcomes());
        }
        if (now.isBefore(startsAt)) {
            throw new InvalidGameActionException("Typing race countdown is still active");
        }
        if (!"TYPE_CHARACTER".equals(input.action()) || input.row() != null || input.column() != null) {
            throw new InvalidGameActionException("Typing race accepts character input only");
        }
        if (input.sequence() == null || input.sequence() != player.lastInputSequence + 1) {
            throw new InvalidGameActionException("Typing input sequence must increase by exactly one");
        }
        String character = normalizeCharacter(input.character());
        String expected = codePointAt(passage.text(), player.progress);

        player.lastInputSequence = input.sequence();
        player.lastInputAt = now;
        if (expected.equals(character)) {
            player.progress++;
            player.correctCharacters++;
            player.combo++;
            player.bestCombo = Math.max(player.bestCombo, player.combo);
            if (player.progress == passageLength) {
                player.finishedAt = now;
                winnerId = userId;
                endedAt = now;
            }
        } else {
            player.errors++;
            player.combo = 0;
        }
        sequence++;
        return new EngineUpdate(true, snapshot(), terminal(), terminal() ? outcomes() : List.of());
    }

    @Override
    public EngineUpdate tick(Instant now) {
        if (terminal()) {
            return new EngineUpdate(false, snapshot(), true, outcomes());
        }
        if (now.isBefore(deadline)) {
            return new EngineUpdate(false, snapshot(), false, List.of());
        }
        settleTimeout();
        sequence++;
        return new EngineUpdate(true, snapshot(), true, outcomes());
    }

    @Override
    public boolean terminal() {
        return winnerId != null || draw;
    }

    @Override
    public List<EngineOutcome> outcomes() {
        if (!terminal()) return List.of();
        return players.values().stream().map(player -> new EngineOutcome(player.userId,
                draw ? GameResultType.DRAW : player.userId.equals(winnerId)
                        ? GameResultType.WIN : GameResultType.LOSS,
                player.wpm(endedAt))).toList();
    }

    private void settleTimeout() {
        endedAt = deadline;
        List<MutablePlayer> ranking = players.values().stream()
                .sorted(Comparator.comparingInt((MutablePlayer player) -> player.progress).reversed()
                        .thenComparingInt(player -> player.errors))
                .toList();
        MutablePlayer first = ranking.getFirst();
        MutablePlayer second = ranking.get(1);
        if (first.progress == second.progress && first.errors == second.errors) {
            draw = true;
        } else {
            winnerId = first.userId;
        }
    }

    private MutablePlayer restorePlayer(TypingRaceCheckpoint.PlayerState state) {
        boolean noInputs = state.lastInputSequence() == -1;
        long attempts = (long) state.correctCharacters() + state.errors();
        if (state.userId() == null || state.progress() < 0 || state.progress() > passageLength
                || state.correctCharacters() != state.progress() || state.errors() < 0
                || state.combo() < 0 || state.bestCombo() < state.combo()
                || state.bestCombo() > state.correctCharacters() || state.lastInputSequence() < -1
                || attempts != state.lastInputSequence() + 1
                || noInputs != (state.lastInputAt() == null)
                || state.lastInputAt() != null && (state.lastInputAt().isBefore(startsAt)
                        || state.lastInputAt().isAfter(deadline))
                || (state.progress() == passageLength) != (state.finishedAt() != null)
                || state.finishedAt() != null && (state.finishedAt().isBefore(startsAt)
                        || state.finishedAt().isAfter(deadline))) {
            throw new IllegalArgumentException("Typing Race player checkpoint is invalid");
        }
        MutablePlayer player = new MutablePlayer(state.userId());
        player.progress = state.progress();
        player.correctCharacters = state.correctCharacters();
        player.errors = state.errors();
        player.combo = state.combo();
        player.bestCombo = state.bestCombo();
        player.lastInputSequence = state.lastInputSequence();
        player.lastInputAt = state.lastInputAt();
        player.finishedAt = state.finishedAt();
        return player;
    }

    private void validateTerminalState() {
        List<MutablePlayer> finished = players.values().stream()
                .filter(player -> player.finishedAt != null).toList();
        if (winnerId == null && !draw && !finished.isEmpty()
                || winnerId != null && endedAt == null
                || draw && endedAt == null
                || winnerId != null && endedAt.isBefore(startsAt)
                || endedAt != null && endedAt.isAfter(deadline)) {
            throw new IllegalArgumentException("Typing Race checkpoint outcome is invalid");
        }
        if (!finished.isEmpty()) {
            MutablePlayer winner = players.get(winnerId);
            if (draw || finished.size() != 1 || winner == null
                    || winner.finishedAt == null || !winner.finishedAt.equals(endedAt)) {
                throw new IllegalArgumentException("Typing Race finished checkpoint is invalid");
            }
        } else if (winnerId != null || draw) {
            if (!deadline.equals(endedAt)) {
                throw new IllegalArgumentException("Typing Race timeout checkpoint is invalid");
            }
        }
    }

    private static String normalizeCharacter(String value) {
        if (value == null) throw new InvalidGameActionException("Typing character is required");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            throw new InvalidGameActionException("Typing input must contain exactly one character");
        }
        return normalized;
    }

    private static String codePointAt(String value, int index) {
        int offset = value.offsetByCodePoints(0, index);
        int codePoint = value.codePointAt(offset);
        return new String(Character.toChars(codePoint));
    }

    private static void validatePlayers(List<UUID> playerIds) {
        if (playerIds == null || playerIds.size() != 2
                || playerIds.stream().anyMatch(java.util.Objects::isNull)
                || playerIds.stream().distinct().count() != 2) {
            throw new IllegalArgumentException("Typing Race requires two distinct players");
        }
    }

    private final class MutablePlayer {
        private final UUID userId;
        private int progress;
        private int correctCharacters;
        private int errors;
        private int combo;
        private int bestCombo;
        private long lastInputSequence = -1;
        private Instant lastInputAt;
        private Instant finishedAt;

        private MutablePlayer(UUID userId) {
            this.userId = userId;
        }

        private TypingRacePlayerSnapshot snapshot(Instant terminalAt) {
            return new TypingRacePlayerSnapshot(userId, progress, correctCharacters, errors, combo,
                    bestCombo, lastInputSequence, wpm(terminalAt), accuracy(), finishedAt != null, finishedAt);
        }

        private TypingRaceCheckpoint.PlayerState checkpoint() {
            return new TypingRaceCheckpoint.PlayerState(userId, progress, correctCharacters, errors,
                    combo, bestCombo, lastInputSequence, lastInputAt, finishedAt);
        }

        private int wpm(Instant terminalAt) {
            Instant measuredAt = finishedAt != null ? finishedAt : terminalAt != null ? terminalAt : lastInputAt;
            if (correctCharacters == 0 || measuredAt == null) return 0;
            long elapsedMillis = Math.max(1_000, Duration.between(startsAt, measuredAt).toMillis());
            return Math.toIntExact(Math.min(2_000, correctCharacters * 12_000L / elapsedMillis));
        }

        private int accuracy() {
            int attempts = correctCharacters + errors;
            return attempts == 0 ? 100 : (int) Math.round(correctCharacters * 100.0 / attempts);
        }
    }
}
