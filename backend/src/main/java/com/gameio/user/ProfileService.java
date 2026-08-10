package com.gameio.user;

import com.gameio.achievement.AchievementService;
import com.gameio.gameresult.GameResultRepository;
import com.gameio.gameresult.GameResultType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final GameResultRepository results;
    private final AchievementService achievements;
    private final Clock clock;

    public ProfileService(
            UserRepository users,
            GameResultRepository results,
            AchievementService achievements,
            Clock clock) {
        this.users = users;
        this.results = results;
        this.achievements = achievements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(users.findById(userId).orElseThrow(UserNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public ProfileResponse findByUsername(String username) {
        UserAccount user = users.findByUsernameNormalized(UserAccount.normalize(username))
                .orElseThrow(UserNotFoundException::new);
        return profile(user);
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateProfileRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        String avatarUrl = request.avatarUrl() == null || request.avatarUrl().isBlank()
                ? null : request.avatarUrl().trim();
        user.updateProfile(avatarUrl, Instant.now(clock));
        return UserResponse.from(user);
    }

    private ProfileResponse profile(UserAccount user) {
        UUID userId = user.getId();
        return new ProfileResponse(userId, user.getUsername(), user.getAvatarUrl(), user.getLevel(), user.getExp(),
                user.getCreatedAt(), results.countByPlayerId(userId),
                results.countByPlayerIdAndResult(userId, GameResultType.WIN), achievements.listForUser(userId));
    }
}
