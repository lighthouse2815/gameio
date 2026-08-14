package com.gameio.dailychallenge;

import com.gameio.common.security.CurrentUser;
import com.gameio.gameresult.GameSessionResponse;
import com.gameio.leaderboard.LeaderboardResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/daily-challenges")
public class DailyChallengeController {
    private final DailyChallengeService challenges;
    private final CurrentUser currentUser;

    public DailyChallengeController(DailyChallengeService challenges, CurrentUser currentUser) {
        this.challenges = challenges;
        this.currentUser = currentUser;
    }

    @GetMapping("/today")
    DailyChallengeResponse today() {
        return challenges.today();
    }

    @PostMapping("/today/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    GameSessionResponse startToday(Authentication authentication) {
        return challenges.startToday(currentUser.id(authentication));
    }

    @GetMapping("/me")
    DailyChallengeProgressResponse progress(Authentication authentication) {
        return challenges.progress(currentUser.id(authentication));
    }

    @GetMapping("/{date}/leaderboard")
    LeaderboardResponse leaderboard(
            @PathVariable LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return challenges.leaderboard(date, page, size);
    }
}
