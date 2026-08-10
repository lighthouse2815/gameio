package com.gameio.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 500)
        @Pattern(regexp = "https://.+", message = "must be an HTTPS URL")
        String avatarUrl
) {
}
