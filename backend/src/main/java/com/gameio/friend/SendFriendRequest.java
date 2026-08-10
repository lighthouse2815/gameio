package com.gameio.friend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendFriendRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]{3,24}", message = "must contain 3-24 letters, numbers, or underscores")
        String username
) {
}
