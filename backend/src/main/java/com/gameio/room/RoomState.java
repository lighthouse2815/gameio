package com.gameio.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RoomState(
        UUID roomId,
        String roomCode,
        UUID gameId,
        String gameSlug,
        String gameName,
        UUID ownerId,
        int minPlayers,
        int maxPlayers,
        boolean privateRoom,
        RoomStatus status,
        List<RoomPlayer> players,
        Instant createdAt,
        Instant expiresAt
) {
    public RoomState {
        players = List.copyOf(players);
    }

    public boolean hasPlayer(UUID userId) {
        return players.stream().anyMatch(player -> player.id().equals(userId));
    }

    public boolean canStart() {
        return status == RoomStatus.WAITING
                && players.size() >= minPlayers
                && players.stream().allMatch(player -> player.ready() && player.connected());
    }

    public RoomState addPlayer(RoomPlayer player) {
        List<RoomPlayer> updated = new ArrayList<>(players);
        updated.add(player);
        return copy(ownerId, status, updated);
    }

    public RoomState ready(UUID userId) {
        return copy(ownerId, status, players.stream()
                .map(player -> player.id().equals(userId) ? player.readyUp() : player).toList());
    }

    public RoomState connected(UUID userId, boolean connected) {
        return copy(ownerId, status, players.stream()
                .map(player -> player.id().equals(userId) ? player.withConnection(connected) : player).toList());
    }

    public RoomState removePlayer(UUID userId) {
        List<RoomPlayer> remaining = players.stream().filter(player -> !player.id().equals(userId)).toList();
        if (remaining.isEmpty()) {
            return copy(ownerId, status, remaining);
        }
        UUID nextOwner = ownerId.equals(userId) ? remaining.getFirst().id() : ownerId;
        List<RoomPlayer> ownership = remaining.stream()
                .map(player -> player.withOwner(player.id().equals(nextOwner))).toList();
        return copy(nextOwner, status, ownership);
    }

    public RoomState playing() {
        return copy(ownerId, RoomStatus.PLAYING, players);
    }

    public RoomState finished() {
        return copy(ownerId, RoomStatus.FINISHED, players);
    }

    public RoomState waitingForRematch(UUID connectedUserId) {
        List<RoomPlayer> waitingPlayers = players.stream()
                .map(player -> player.waitingForRematch(player.id().equals(connectedUserId)))
                .toList();
        return copy(ownerId, RoomStatus.WAITING, waitingPlayers);
    }

    public RoomState reconnectForRematch(UUID connectedUserId) {
        return copy(ownerId, status, players.stream()
                .map(player -> player.id().equals(connectedUserId)
                        ? player.withConnection(true)
                        : player)
                .toList());
    }

    private RoomState copy(UUID newOwnerId, RoomStatus newStatus, List<RoomPlayer> newPlayers) {
        return new RoomState(roomId, roomCode, gameId, gameSlug, gameName, newOwnerId, minPlayers, maxPlayers,
                privateRoom, newStatus, newPlayers, createdAt, expiresAt);
    }
}
