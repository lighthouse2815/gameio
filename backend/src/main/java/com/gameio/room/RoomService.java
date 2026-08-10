package com.gameio.room;

import com.gameio.common.web.PageResponse;
import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.game.GameType;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RoomService {
    private static final Duration ROOM_TTL = Duration.ofHours(6);
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final RoomStore rooms;
    private final GameRepository games;
    private final UserRepository users;
    private final RoomEventSink events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Object createLock = new Object();
    private final Object membershipLock = new Object();
    private final ConcurrentHashMap<UUID, Object> roomLocks = new ConcurrentHashMap<>();

    public RoomService(
            RoomStore rooms,
            GameRepository games,
            UserRepository users,
            RoomEventSink events,
            Clock clock) {
        this.rooms = rooms;
        this.games = games;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    public RoomResponse create(UUID ownerId, CreateRoomRequest request) {
        Game game = requireMultiplayerGame(request.gameId());
        if (request.maxPlayers() < game.getMinPlayers() || request.maxPlayers() > game.getMaxPlayers()) {
            throw new InvalidRoomActionException("INVALID_ROOM_CAPACITY",
                    "Room capacity is outside the selected game's player limits");
        }
        UserAccount owner = users.findById(ownerId).orElseThrow(UserNotFoundException::new);
        synchronized (membershipLock) {
            requireNoOtherActiveRoom(ownerId, null);
            RoomState room = createRoom(game, owner, request.maxPlayers(), request.privateRoom(), List.of());
            return RoomResponse.from(room);
        }
    }

    public RoomState createForMatchmaking(UUID gameId, List<UUID> playerIds) {
        Game game = requireMultiplayerGame(gameId);
        if (playerIds.size() < game.getMinPlayers() || playerIds.size() > game.getMaxPlayers()
                || playerIds.stream().distinct().count() != playerIds.size()) {
            throw new InvalidRoomActionException("INVALID_MATCH_SIZE", "Match size is invalid for this game");
        }
        List<UserAccount> players = playerIds.stream()
                .map(id -> users.findById(id).orElseThrow(UserNotFoundException::new)).toList();
        synchronized (membershipLock) {
            players.forEach(player -> requireNoOtherActiveRoom(player.getId(), null));
            return createRoom(game, players.getFirst(), game.getMinPlayers(), true,
                    players.subList(1, players.size()));
        }
    }

    public RoomResponse join(UUID userId, String roomReference) {
        RoomState found = findByReference(roomReference);
        synchronized (membershipLock) {
            requireNoOtherActiveRoom(userId, found.roomId());
            synchronized (lock(found.roomId())) {
                RoomState room = requireRoom(found.roomId());
                if (room.status() == RoomStatus.FINISHED) {
                    throw new InvalidRoomActionException("ROOM_FINISHED", "Room has already finished");
                }
                if (room.hasPlayer(userId)) {
                    return RoomResponse.from(room);
                }
                if (room.status() != RoomStatus.WAITING) {
                    throw new InvalidRoomActionException("ROOM_NOT_JOINABLE", "Room is not accepting new players");
                }
                if (room.players().size() >= room.maxPlayers()) {
                    throw new InvalidRoomActionException("ROOM_FULL", "Room is full");
                }
                UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
                RoomState joined = room.addPlayer(
                        new RoomPlayer(user.getId(), user.getUsername(), false, false, false));
                rooms.save(joined);
                events.roomUpdated(joined);
                return RoomResponse.from(joined);
            }
        }
    }

    public void leave(UUID userId, UUID roomId) {
        synchronized (membershipLock) {
            synchronized (lock(roomId)) {
                RoomState room = requireMembership(requireRoom(roomId), userId);
                if (room.status() == RoomStatus.PLAYING) {
                    RoomState disconnected = room.connected(userId, false);
                    rooms.save(disconnected);
                    events.playerDisconnected(disconnected, userId);
                    events.roomUpdated(disconnected);
                    return;
                }
                RoomState left = room.removePlayer(userId);
                if (left.players().isEmpty()) {
                    rooms.delete(room);
                    roomLocks.remove(roomId);
                } else {
                    rooms.save(left);
                    events.roomUpdated(left);
                }
            }
        }
    }

    public RoomResponse ready(UUID userId, UUID roomId) {
        synchronized (lock(roomId)) {
            RoomState room = requireMembership(requireRoom(roomId), userId);
            if (room.status() != RoomStatus.WAITING) {
                throw new InvalidRoomActionException("ROOM_ALREADY_STARTED", "Room is no longer waiting");
            }
            RoomState ready = room.ready(userId);
            rooms.save(ready);
            events.roomUpdated(ready);
            return RoomResponse.from(ready);
        }
    }

    public RoomResponse start(UUID userId, UUID roomId) {
        synchronized (lock(roomId)) {
            RoomState room = requireMembership(requireRoom(roomId), userId);
            if (!room.ownerId().equals(userId)) {
                throw new InvalidRoomActionException("ROOM_OWNER_REQUIRED", "Only the room owner can start the game");
            }
            if (!room.canStart()) {
                throw new InvalidRoomActionException("ROOM_NOT_READY", "All required players must be ready");
            }
            RoomState playing = room.playing();
            rooms.save(playing);
            events.roomUpdated(playing);
            events.gameStarted(playing);
            return RoomResponse.from(playing);
        }
    }

    public RoomResponse getForMember(UUID userId, UUID roomId) {
        return RoomResponse.from(requireMembership(requireRoom(roomId), userId));
    }

    public RoomState reconnect(UUID userId, String roomReference) {
        RoomState found = findByReference(roomReference);
        synchronized (lock(found.roomId())) {
            RoomState room = requireMembership(requireRoom(found.roomId()), userId).connected(userId, true);
            rooms.save(room);
            events.roomUpdated(room);
            return room;
        }
    }

    public UUID resolveRoomId(String roomReference) {
        return findByReference(roomReference).roomId();
    }

    public void requireAvailableForNewRoom(UUID userId) {
        synchronized (membershipLock) {
            requireNoOtherActiveRoom(userId, null);
        }
    }

    public void expireUnrestorableMatch(UUID roomId) {
        Object roomLock = lock(roomId);
        synchronized (roomLock) {
            RoomState room = rooms.findById(roomId).orElse(null);
            if (room == null) {
                roomLocks.remove(roomId, roomLock);
            } else if (room.status() == RoomStatus.PLAYING) {
                rooms.delete(room);
                roomLocks.remove(roomId, roomLock);
            }
        }
    }

    public PageResponse<RoomResponse> list(UUID gameId, RoomStatus status, int page, int size) {
        List<RoomResponse> filtered = rooms.findAll().stream()
                .filter(room -> !room.privateRoom())
                .filter(room -> gameId == null || room.gameId().equals(gameId))
                .filter(room -> status == null || room.status() == status)
                .sorted(Comparator.comparing(RoomState::createdAt).reversed())
                .map(RoomResponse::from)
                .toList();
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        return new PageResponse<>(filtered.subList(from, to), page, size, filtered.size(), totalPages);
    }

    private RoomState createRoom(
            Game game, UserAccount owner, int maxPlayers, boolean privateRoom, List<UserAccount> additionalPlayers) {
        synchronized (createLock) {
            Instant now = Instant.now(clock);
            String code = uniqueCode();
            List<RoomPlayer> players = new ArrayList<>();
            players.add(new RoomPlayer(owner.getId(), owner.getUsername(), false, true, false));
            additionalPlayers.forEach(player -> players.add(
                    new RoomPlayer(player.getId(), player.getUsername(), false, false, false)));
            RoomState room = new RoomState(UUID.randomUUID(), code, game.getId(), game.getSlug(), game.getName(),
                    owner.getId(), game.getMinPlayers(), maxPlayers, privateRoom, RoomStatus.WAITING,
                    players, now, now.plus(ROOM_TTL));
            rooms.save(room);
            events.roomUpdated(room);
            return room;
        }
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            if (rooms.findByCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not allocate a unique room code");
    }

    private Game requireMultiplayerGame(UUID gameId) {
        return games.findById(gameId)
                .filter(Game::isEnabled)
                .filter(game -> game.getGameType() != GameType.SINGLE_PLAYER)
                .orElseThrow(GameNotFoundException::new);
    }

    private RoomState findByReference(String roomReference) {
        try {
            return rooms.findById(UUID.fromString(roomReference)).orElseThrow(RoomNotFoundException::new);
        } catch (IllegalArgumentException exception) {
            return rooms.findByCode(roomReference.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(RoomNotFoundException::new);
        }
    }

    private RoomState requireRoom(UUID roomId) {
        return rooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
    }

    private RoomState requireMembership(RoomState room, UUID userId) {
        if (!room.hasPlayer(userId)) {
            throw new InvalidRoomActionException("ROOM_MEMBERSHIP_REQUIRED", "User is not a member of this room");
        }
        return room;
    }

    private void requireNoOtherActiveRoom(UUID userId, UUID allowedRoomId) {
        boolean alreadyInAnotherRoom = rooms.findAll().stream()
                .filter(room -> room.status() != RoomStatus.FINISHED)
                .filter(room -> allowedRoomId == null || !room.roomId().equals(allowedRoomId))
                .anyMatch(room -> room.hasPlayer(userId));
        if (alreadyInAnotherRoom) {
            throw new InvalidRoomActionException("ALREADY_IN_ANOTHER_ROOM",
                    "Leave the active room before joining or creating another room");
        }
    }

    private Object lock(UUID roomId) {
        return roomLocks.computeIfAbsent(roomId, ignored -> new Object());
    }
}
