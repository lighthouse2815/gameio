package com.gameio.stats;

import com.gameio.common.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
class PlayerStatsController {
    private final PlayerStatsService stats;
    private final CurrentUser currentUser;

    PlayerStatsController(PlayerStatsService stats, CurrentUser currentUser) {
        this.stats = stats;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    PlayerStatsResponse me(Authentication authentication) {
        return stats.stats(currentUser.id(authentication));
    }
}
