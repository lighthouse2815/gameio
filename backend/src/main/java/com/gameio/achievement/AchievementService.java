package com.gameio.achievement;

import com.gameio.user.LevelService;
import com.gameio.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AchievementService {
    private final AchievementRepository achievements;
    private final PlayerAchievementRepository playerAchievements;
    private final LevelService levelService;
    private final Clock clock;

    public AchievementService(
            AchievementRepository achievements,
            PlayerAchievementRepository playerAchievements,
            LevelService levelService,
            Clock clock) {
        this.achievements = achievements;
        this.playerAchievements = playerAchievements;
        this.levelService = levelService;
        this.clock = clock;
    }

    public List<UnlockedAchievementResponse> evaluate(UserAccount user, AchievementProgress progress) {
        Set<String> eligibleCodes = new LinkedHashSet<>();
        if (progress.completedGames() >= 1) eligibleCodes.add("FIRST_GAME");
        if (progress.wins() >= 1) eligibleCodes.add("FIRST_WIN");
        if (progress.completedGames() >= 10) eligibleCodes.add("PLAY_10_GAMES");
        if (progress.wins() >= 10) eligibleCodes.add("WIN_10_GAMES");
        if (progress.snakeBestScore() >= 1000) eligibleCodes.add("SCORE_1000_SNAKE");
        if (progress.ticTacToeWins() >= 5) eligibleCodes.add("WIN_5_TICTACTOE");

        List<UnlockedAchievementResponse> unlocked = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (Achievement achievement : achievements.findByCodeIn(eligibleCodes)) {
            if (!playerAchievements.existsByIdUserIdAndIdAchievementId(user.getId(), achievement.getId())) {
                PlayerAchievement playerAchievement = new PlayerAchievement(user, achievement, now);
                playerAchievements.save(playerAchievement);
                levelService.grant(user, achievement.getExpReward());
                unlocked.add(UnlockedAchievementResponse.from(playerAchievement));
            }
        }
        return List.copyOf(unlocked);
    }

    @Transactional(readOnly = true)
    public List<AchievementResponse> listAll() {
        return achievements.findAllByOrderByNameAsc().stream().map(AchievementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UnlockedAchievementResponse> listForUser(UUID userId) {
        return playerAchievements.findByIdUserIdOrderByUnlockedAtDesc(userId).stream()
                .map(UnlockedAchievementResponse::from).toList();
    }
}
