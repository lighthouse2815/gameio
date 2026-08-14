package com.gameio.multiplayer;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.competition.TournamentMatchCompletedEvent;
import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativeMatchResult;
import com.gameio.gameresult.multiplayer.AuthoritativePlayerOutcome;
import com.gameio.gameresult.multiplayer.AuthoritativeResultService;
import com.gameio.gameresult.multiplayer.PlayerProgression;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineOutcome;
import com.gameio.multiplayer.engine.EngineRegistry;
import com.gameio.multiplayer.engine.EngineUpdate;
import com.gameio.multiplayer.engine.GameInput;
import com.gameio.multiplayer.presence.PresenceStore;
import com.gameio.observability.GameioOperationalMetrics;
import com.gameio.room.RoomEventSink;
import com.gameio.room.RoomResponse;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RealtimeGameCoordinator implements RoomEventSink {
    private static final Logger log = LoggerFactory.getLogger(RealtimeGameCoordinator.class);
    private static final Duration RECONNECT_GRACE = Duration.ofSeconds(60);
    static final Duration MAX_IDLE_DURATION = Duration.ofMinutes(5);
    static final Duration MAX_MATCH_DURATION = Duration.ofMinutes(30);

    private final Map<UUID, ActiveMatch> matches = new ConcurrentHashMap<>();
    private final EngineRegistry engines;
    private final RealtimePublisher realtime;
    private final RoomStore rooms;
    private final AuthoritativeResultService resultService;
    private final PresenceStore presence;
    private final RealtimeSessionRegistry sessions;
    private final ActiveMatchStore checkpointStore;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final GameioOperationalMetrics metrics;

    public RealtimeGameCoordinator(
            EngineRegistry engines,
            RealtimePublisher realtime,
            RoomStore rooms,
            AuthoritativeResultService resultService,
            PresenceStore presence,
            RealtimeSessionRegistry sessions,
            ActiveMatchStore checkpointStore,
            ApplicationEventPublisher events,
            GameioOperationalMetrics metrics,
            Clock clock) {
        this.engines = engines;
        this.realtime = realtime;
        this.rooms = rooms;
        this.resultService = resultService;
        this.presence = presence;
        this.sessions = sessions;
        this.checkpointStore = checkpointStore;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public void roomUpdated(RoomState room) {
        realtime.toRoom(room.roomId(), "ROOM_STATE", RoomResponse.from(room), null);
    }

    @Override
    public void gameStarted(RoomState room) {
        AtomicBoolean created = new AtomicBoolean();
        ActiveMatch active = matches.computeIfAbsent(room.roomId(), ignored -> {
            created.set(true);
            AuthoritativeEngine engine = engines.create(room.gameSlug(),
                    room.players().stream().map(com.gameio.room.RoomPlayer::id).toList());
            return new ActiveMatch(UUID.randomUUID(), room, engine, Instant.now(clock));
        });
        if (created.get()) metrics.matchStarted();
        metrics.activeMatches(matches.size());
        persistCheckpoint(active);
        room.players().forEach(player ->
                presence.online(player.id(), room.roomId(), room.gameSlug(), room.gameName()));
        realtime.toRoom(room.roomId(), "GAME_START",
                new GameStartPayload(active.matchId, room.gameId(), room.gameSlug(), room.players(),
                        active.startedAt, active.engine.snapshot()), null);
    }

    @Override
    public void playerDisconnected(RoomState room, UUID userId) {
        disconnect(room.roomId(), userId);
    }

    public void input(UUID roomId, UUID userId, GameInput input, String requestId) {
        ActiveMatch active = requireMatch(roomId);
        if (!active.room.hasPlayer(userId)) {
            throw new InvalidGameActionException("Player is not a member of this active match");
        }
        synchronized (active) {
            Instant now = Instant.now(clock);
            EngineUpdate update = active.engine.input(userId, input, now);
            metrics.inputAccepted();
            active.lastActivityAt = now;
            if (update.changed()) {
                persistCheckpoint(active);
                realtime.toRoom(roomId, "GAME_STATE", update.snapshot(), requestId);
            }
            if (update.terminal()) {
                finish(active, update.snapshot(), update.outcomes());
            }
        }
    }

    public boolean reconnect(UUID roomId, UUID userId) {
        ActiveMatch active = findOrRestore(roomId);
        if (active == null) return false;
        synchronized (active) {
            active.disconnectedAt.remove(userId);
            active.lastActivityAt = Instant.now(clock);
            persistCheckpoint(active);
            if (active.engine.terminal()) {
                finish(active, active.engine.snapshot(), active.engine.outcomes());
                return true;
            }
            realtime.toUser(userId, "GAME_STATE", roomId, active.engine.snapshot(), null);
        }
        return true;
    }

    public void snapshotTo(UUID roomId, UUID userId) {
        ActiveMatch active = requireMatch(roomId);
        realtime.toUser(userId, "GAME_STATE", roomId, active.engine.snapshot(), null);
    }

    public GameStartPayload spectatorStart(UUID roomId) {
        ActiveMatch active = requireMatch(roomId);
        synchronized (active) {
            return new GameStartPayload(active.matchId, active.room.gameId(), active.room.gameSlug(),
                    active.room.players(), active.startedAt, active.engine.snapshot());
        }
    }

    public void disconnect(UUID roomId, UUID userId) {
        ActiveMatch active = findOrRestore(roomId);
        if (active == null) return;
        synchronized (active) {
            active.disconnectedAt.putIfAbsent(userId, Instant.now(clock));
            persistCheckpoint(active);
            realtime.toRoom(roomId, "OPPONENT_DISCONNECTED",
                    new OpponentDisconnectedPayload(userId, Math.toIntExact(RECONNECT_GRACE.toSeconds())), null);
        }
    }

    @Scheduled(fixedRate = 50)
    void tick() {
        Instant now = Instant.now(clock);
        for (ActiveMatch active : List.copyOf(matches.values())) {
            try {
                synchronized (active) {
                    if (finishDisconnectedMatchIfNeeded(active, now)) continue;
                    if (finishExpiredMatchIfNeeded(active, now)) continue;
                    if (!active.engine.requiresServerTick()) continue;
                    EngineUpdate update = active.engine.tick(now);
                    if (update.changed()) {
                        persistCheckpoint(active);
                        realtime.toRoom(active.room.roomId(), "GAME_STATE", update.snapshot(), null);
                    }
                    if (update.terminal()) {
                        finish(active, update.snapshot(), update.outcomes());
                    }
                }
            } catch (RuntimeException exception) {
                log.error("Realtime tick failed for room {}", active.room.roomId(), exception);
            }
        }
    }

    private boolean finishDisconnectedMatchIfNeeded(ActiveMatch active, Instant now) {
        List<UUID> forfeited = active.disconnectedAt.entrySet().stream()
                .filter(entry -> !entry.getValue().plus(RECONNECT_GRACE).isAfter(now))
                .map(Map.Entry::getKey).toList();
        if (forfeited.isEmpty()) return false;
        List<UUID> connected = active.room.players().stream().map(com.gameio.room.RoomPlayer::id)
                .filter(player -> !forfeited.contains(player)).toList();
        if (connected.size() > 1) return false;
        UUID winner = connected.isEmpty() ? null : connected.getFirst();
        List<EngineOutcome> outcomes = active.room.players().stream().map(player -> new EngineOutcome(player.id(),
                winner == null ? GameResultType.DRAW : player.id().equals(winner)
                        ? GameResultType.WIN : GameResultType.LOSS, player.id().equals(winner) ? 1 : 0)).toList();
        finish(active, active.engine.snapshot(), outcomes);
        return true;
    }

    private boolean finishExpiredMatchIfNeeded(ActiveMatch active, Instant now) {
        boolean matchExpired = !active.startedAt.plus(MAX_MATCH_DURATION).isAfter(now);
        boolean idleExpired = !active.lastActivityAt.plus(MAX_IDLE_DURATION).isAfter(now);
        if (!matchExpired && !idleExpired) return false;
        List<EngineOutcome> outcomes = active.room.players().stream()
                .map(player -> new EngineOutcome(player.id(), GameResultType.DRAW, 0))
                .toList();
        finish(active, active.engine.snapshot(), outcomes);
        return true;
    }

    private void finish(ActiveMatch active, Object finalState, List<EngineOutcome> outcomes) {
        if (!active.completing.compareAndSet(false, true)) return;
        try {
            int duration = Math.max(1, Math.toIntExact(
                    Math.min(86_400, Duration.between(active.startedAt, Instant.now(clock)).toSeconds())));
            AuthoritativeMatchResult result = new AuthoritativeMatchResult(active.matchId, active.room.gameId(),
                    duration, outcomes.stream().map(outcome -> new AuthoritativePlayerOutcome(
                            outcome.userId(), outcome.result(), outcome.score())).toList());
            List<PlayerProgression> progression = resultService.record(result);
            RoomState finished = active.room.finished();
            rooms.save(finished);
            try {
                events.publishEvent(new TournamentMatchCompletedEvent(active.room.roomId(), result.outcomes()));
            } catch (RuntimeException exception) {
                log.error("Tournament bracket update failed for room {}", active.room.roomId(), exception);
            }
            realtime.toRoom(active.room.roomId(), "GAME_OVER",
                    new GameOverPayload(active.matchId, finalState, progression), null);
            deleteCheckpoint(active.room.roomId());
            sessions.clearRoomBindings(active.room.roomId());
            active.room.players().forEach(player -> {
                if (sessions.hasConnections(player.id())) presence.online(player.id(), null, null, null);
                else presence.offline(player.id());
            });
            if (matches.remove(active.room.roomId(), active)) {
                metrics.activeMatches(matches.size());
                metrics.matchCompleted(Duration.ofSeconds(duration));
            }
        } catch (RuntimeException exception) {
            active.completing.set(false);
            throw exception;
        }
    }

    private ActiveMatch requireMatch(UUID roomId) {
        ActiveMatch active = findOrRestore(roomId);
        if (active == null) {
            throw new InvalidGameActionException("Active match was not found or cannot be restored after restart");
        }
        return active;
    }

    private ActiveMatch findOrRestore(UUID roomId) {
        ActiveMatch active = matches.computeIfAbsent(roomId, this::restore);
        metrics.activeMatches(matches.size());
        return active;
    }

    private ActiveMatch restore(UUID roomId) {
        try {
            ActiveMatchCheckpoint checkpoint = checkpointStore.find(roomId).orElse(null);
            if (checkpoint == null) {
                metrics.restoreMissed();
                return null;
            }
            RoomState room = rooms.findById(roomId)
                    .orElseThrow(() -> new IllegalStateException("Active room metadata is missing"));
            List<UUID> players = room.players().stream().map(com.gameio.room.RoomPlayer::id).toList();
            List<UUID> checkpointPlayers = checkpoint.room().players().stream()
                    .map(com.gameio.room.RoomPlayer::id).toList();
            if (room.status() != com.gameio.room.RoomStatus.PLAYING
                    || !room.gameId().equals(checkpoint.room().gameId())
                    || !room.gameSlug().equals(checkpoint.room().gameSlug())
                    || !players.equals(checkpointPlayers)) {
                throw new IllegalStateException("Active match checkpoint does not match room metadata");
            }
            AuthoritativeEngine engine = engines.restore(room.gameSlug(), players, checkpoint.engineState());
            ActiveMatch active = new ActiveMatch(checkpoint.matchId(), room, engine, checkpoint.startedAt());
            active.lastActivityAt = checkpoint.lastActivityAt();
            active.disconnectedAt.putAll(checkpoint.disconnectedAt());
            Instant now = Instant.now(clock);
            room.players().stream()
                    .filter(player -> !sessions.hasRoomConnections(player.id(), roomId))
                    .forEach(player -> active.disconnectedAt.putIfAbsent(player.id(), now));
            log.info("Restored authoritative {} match {} for room {} from checkpoint sequence {}",
                    room.gameSlug(), active.matchId, roomId, checkpoint.engineState().path("sequence").asLong(-1));
            metrics.restoreSucceeded();
            persistCheckpoint(active);
            return active;
        } catch (RuntimeException exception) {
            metrics.restoreFailed();
            log.error("Could not restore active match checkpoint for room {}", roomId, exception);
            deleteCheckpoint(roomId);
            return null;
        }
    }

    private void persistCheckpoint(ActiveMatch active) {
        try {
            checkpointStore.save(new ActiveMatchCheckpoint(active.matchId, active.room, active.startedAt,
                    active.lastActivityAt, active.disconnectedAt, engines.checkpoint(active.engine),
                    Instant.now(clock)));
            metrics.checkpointSaved();
        } catch (RuntimeException exception) {
            metrics.checkpointSaveFailed();
            log.error("Could not persist active match checkpoint for room {}", active.room.roomId(), exception);
        }
    }

    private void deleteCheckpoint(UUID roomId) {
        try {
            checkpointStore.delete(roomId);
            metrics.checkpointDeleted();
        } catch (RuntimeException exception) {
            metrics.checkpointDeleteFailed();
            log.error("Could not delete active match checkpoint for room {}", roomId, exception);
        }
    }

    private static final class ActiveMatch {
        private final UUID matchId;
        private final RoomState room;
        private final AuthoritativeEngine engine;
        private final Instant startedAt;
        private Instant lastActivityAt;
        private final Map<UUID, Instant> disconnectedAt = new ConcurrentHashMap<>();
        private final AtomicBoolean completing = new AtomicBoolean();

        private ActiveMatch(UUID matchId, RoomState room, AuthoritativeEngine engine, Instant startedAt) {
            this.matchId = matchId;
            this.room = room;
            this.engine = engine;
            this.startedAt = startedAt;
            this.lastActivityAt = startedAt;
        }
    }
}
