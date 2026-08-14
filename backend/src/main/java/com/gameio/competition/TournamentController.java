package com.gameio.competition;

import com.gameio.common.security.CurrentUser;
import com.gameio.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/competition/tournaments")
class TournamentController {
    private final TournamentService tournaments;
    private final CurrentUser currentUser;

    TournamentController(TournamentService tournaments, CurrentUser currentUser) {
        this.tournaments = tournaments;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<TournamentSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tournaments.list(page, size);
    }

    @GetMapping("/{tournamentId}")
    TournamentDetailResponse detail(@PathVariable UUID tournamentId) {
        return tournaments.detail(tournamentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TournamentDetailResponse create(
            Authentication authentication, @Valid @RequestBody CreateTournamentRequest request) {
        return tournaments.create(currentUser.id(authentication), request);
    }

    @PostMapping("/{tournamentId}/join")
    TournamentDetailResponse join(Authentication authentication, @PathVariable UUID tournamentId) {
        return tournaments.join(currentUser.id(authentication), tournamentId);
    }

    @PostMapping("/{tournamentId}/start")
    TournamentDetailResponse start(Authentication authentication, @PathVariable UUID tournamentId) {
        return tournaments.start(currentUser.id(authentication), tournamentId);
    }
}
