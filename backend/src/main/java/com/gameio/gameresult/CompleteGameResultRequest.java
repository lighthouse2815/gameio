package com.gameio.gameresult;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CompleteGameResultRequest(
        @NotNull UUID sessionId,
        @NotNull @Size(min = 1, max = 10_000)
        List<@Pattern(regexp = "[A-Za-z0-9:_-]{1,32}") String> actions,
        @Min(1) @Max(86_400) int durationSeconds
) {
}
