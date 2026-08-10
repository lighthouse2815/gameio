package com.gameio.room;

import com.gameio.common.security.CurrentUser;
import com.gameio.common.web.PageResponse;
import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.multiplayer.RoomLeftPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final CurrentUser currentUser;
    private final RealtimeSessionRegistry sessions;

    public RoomController(
            RoomService roomService, CurrentUser currentUser, RealtimeSessionRegistry sessions) {
        this.roomService = roomService;
        this.currentUser = currentUser;
        this.sessions = sessions;
    }

    @GetMapping
    PageResponse<RoomResponse> list(
            @RequestParam(required = false) UUID gameId,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return roomService.list(gameId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomResponse create(Authentication authentication, @Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(currentUser.id(authentication), request);
    }

    @PostMapping("/join")
    RoomResponse join(Authentication authentication, @Valid @RequestBody JoinRoomRequest request) {
        return roomService.join(currentUser.id(authentication), request.roomCode());
    }

    @GetMapping("/{roomId}")
    RoomResponse get(Authentication authentication, @PathVariable UUID roomId) {
        return roomService.getForMember(currentUser.id(authentication), roomId);
    }

    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(Authentication authentication, @PathVariable UUID roomId) {
        UUID userId = currentUser.id(authentication);
        roomService.leave(userId, roomId);
        sessions.clearUserRoomBindings(userId, roomId);
        sessions.toUser(userId, "ROOM_LEFT", roomId, new RoomLeftPayload(userId), null);
    }

    @PostMapping("/{roomId}/ready")
    RoomResponse ready(Authentication authentication, @PathVariable UUID roomId) {
        return roomService.ready(currentUser.id(authentication), roomId);
    }

    @PostMapping("/{roomId}/start")
    RoomResponse start(Authentication authentication, @PathVariable UUID roomId) {
        return roomService.start(currentUser.id(authentication), roomId);
    }
}
