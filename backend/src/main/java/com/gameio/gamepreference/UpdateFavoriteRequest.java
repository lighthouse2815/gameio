package com.gameio.gamepreference;

import jakarta.validation.constraints.NotNull;

public record UpdateFavoriteRequest(@NotNull Boolean favorite) {
}
