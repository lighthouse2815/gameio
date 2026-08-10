package com.gameio.matchmaking;

import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.game.GameType;
import com.gameio.multiplayer.RealtimePublisher;
import com.gameio.room.RoomResponse;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(15);

    private final MatchmakingStore store;
    private final GameRepository games;
    private final RoomService roomService;
    private final RealtimePublisher realtime;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Object> gameLocks = new ConcurrentHashMap<>();

    public MatchmakingService(
            MatchmakingStore store,
            GameRepository games,
            RoomService roomService,
            RealtimePublisher realtime,
            Clock clock) {
        this.store = store;
        this.games = games;
        this.roomService = roomService;
        this.realtime = realtime;
        this.clock = clock;
    }

    public MatchmakingTicketResponse join(UUID userId, UUID gameId) {
        Game game = games.findById(gameId).filter(Game::isEnabled)
                .filter(candidate -> candidate.getGameType() != GameType.SINGLE_PLAYER)
                .orElseThrow(GameNotFoundException::new);
        MatchmakingTicket existing = store.findByUser(userId).orElse(null);
        if (existing != null) {
            if (existing.status() == MatchmakingStatus.QUEUED) {
                try {
                    roomService.requireAvailableForNewRoom(userId);
                } catch (com.gameio.room.InvalidRoomActionException exception) {
                    store.remove(userId);
                    throw exception;
                }
            }
            return MatchmakingTicketResponse.from(existing);
        }
        roomService.requireAvailableForNewRoom(userId);

        synchronized (gameLocks.computeIfAbsent(gameId, ignored -> new Object())) {
            Instant now = Instant.now(clock);
            MatchmakingTicket ticket = new MatchmakingTicket(UUID.randomUUID(), userId, gameId,
                    MatchmakingStatus.QUEUED, null, now, now.plus(TICKET_TTL));
            store.enqueue(ticket);
            List<MatchmakingTicket> matched = store.takeOldest(gameId, game.getMinPlayers());
            if (matched.isEmpty()) {
                return MatchmakingTicketResponse.from(ticket);
            }

            List<MatchmakingTicket> eligible = new ArrayList<>();
            for (MatchmakingTicket candidate : matched) {
                try {
                    roomService.requireAvailableForNewRoom(candidate.userId());
                    eligible.add(candidate);
                } catch (com.gameio.room.InvalidRoomActionException exception) {
                    store.remove(candidate.userId());
                }
            }
            if (eligible.size() < game.getMinPlayers()) {
                eligible.forEach(store::enqueue);
                if (eligible.stream().noneMatch(candidate -> candidate.userId().equals(userId))) {
                    roomService.requireAvailableForNewRoom(userId);
                }
                return MatchmakingTicketResponse.from(ticket);
            }

            RoomState room = roomService.createForMatchmaking(gameId,
                    eligible.stream().map(MatchmakingTicket::userId).toList());
            for (MatchmakingTicket queued : eligible) {
                MatchmakingTicket found = queued.matched(room.roomId());
                store.saveMatched(found);
                realtime.toUser(found.userId(), "MATCH_FOUND", room.roomId(), RoomResponse.from(room), null);
                if (found.userId().equals(userId)) {
                    ticket = found;
                }
            }
            return MatchmakingTicketResponse.from(ticket);
        }
    }

    public MatchmakingTicketResponse current(UUID userId) {
        return store.findByUser(userId).map(MatchmakingTicketResponse::from)
                .orElseThrow(() -> new MatchmakingNotFoundException());
    }

    public void leave(UUID userId) {
        store.remove(userId);
    }
}
