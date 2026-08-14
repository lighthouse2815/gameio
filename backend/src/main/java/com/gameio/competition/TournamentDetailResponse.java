package com.gameio.competition;

import java.util.List;

public record TournamentDetailResponse(
        TournamentSummaryResponse tournament,
        List<TournamentPlayerResponse> players,
        List<TournamentMatchResponse> matches
) {
}
