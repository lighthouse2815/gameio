package com.gameio.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinRoomRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{6,8}") String roomCode
) {
}
