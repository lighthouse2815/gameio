package com.gameio.achievement;

import com.gameio.common.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {
    private final AchievementService achievementService;
    private final CurrentUser currentUser;

    public AchievementController(AchievementService achievementService, CurrentUser currentUser) {
        this.achievementService = achievementService;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<AchievementResponse> listAll() {
        return achievementService.listAll();
    }

    @GetMapping("/me")
    List<UnlockedAchievementResponse> listMine(Authentication authentication) {
        return achievementService.listForUser(currentUser.id(authentication));
    }
}
