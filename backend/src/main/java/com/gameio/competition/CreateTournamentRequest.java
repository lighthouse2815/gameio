package com.gameio.competition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTournamentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull UUID gameId,
        @NotNull @Pattern(regexp = "4|8|16", message = "must be 4, 8, or 16") String maxPlayers
) {
    int parsedMaxPlayers() {
        return Integer.parseInt(maxPlayers);
    }
}
