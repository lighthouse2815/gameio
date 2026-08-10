package com.gameio.gameresult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartGameSessionRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9-]{2,80}") String gameSlug
) {
}
