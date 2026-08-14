package com.gameio.competition;

import com.gameio.common.security.CurrentUser;
import com.gameio.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/competition")
class CompetitionController {
    private final CompetitionService competition;
    private final CurrentUser currentUser;

    CompetitionController(CompetitionService competition, CurrentUser currentUser) {
        this.competition = competition;
        this.currentUser = currentUser;
    }

    @GetMapping("/season")
    SeasonResponse currentSeason() {
        return competition.currentSeason();
    }

    @GetMapping("/ratings")
    PageResponse<RatingEntryResponse> ratings(
            @RequestParam UUID gameId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return competition.leaderboard(gameId, page, size);
    }

    @GetMapping("/ratings/me")
    List<RatingEntryResponse> mine(Authentication authentication) {
        return competition.mine(currentUser.id(authentication));
    }
}
