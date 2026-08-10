package com.gameio.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomStore {
    void save(RoomState room);

    Optional<RoomState> findById(UUID roomId);

    Optional<RoomState> findByCode(String roomCode);

    List<RoomState> findAll();

    void delete(RoomState room);
}
