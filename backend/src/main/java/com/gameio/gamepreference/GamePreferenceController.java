package com.gameio.gamepreference;

import com.gameio.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game-preferences")
class GamePreferenceController {
    private final GamePreferenceService preferences;
    private final CurrentUser currentUser;

    GamePreferenceController(GamePreferenceService preferences, CurrentUser currentUser) {
        this.preferences = preferences;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    List<GamePreferenceResponse> list(Authentication authentication) {
        return preferences.list(currentUser.id(authentication));
    }

    @PutMapping("/{gameId}/favorite")
    GamePreferenceResponse updateFavorite(
            Authentication authentication,
            @PathVariable UUID gameId,
            @Valid @RequestBody UpdateFavoriteRequest request) {
        return preferences.updateFavorite(currentUser.id(authentication), gameId, request.favorite());
    }

    @PostMapping("/{gameId}/played")
    @ResponseStatus(HttpStatus.CREATED)
    GamePreferenceResponse markPlayed(Authentication authentication, @PathVariable UUID gameId) {
        return preferences.markPlayed(currentUser.id(authentication), gameId);
    }
}
