package com.gameio.gameresult;

import com.gameio.common.security.CurrentUser;
import com.gameio.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/game-results")
public class GameResultController {
    private final GameResultService gameResultService;
    private final CurrentUser currentUser;

    public GameResultController(GameResultService gameResultService, CurrentUser currentUser) {
        this.gameResultService = gameResultService;
        this.currentUser = currentUser;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    GameSessionResponse startSession(
            Authentication authentication, @Valid @RequestBody StartGameSessionRequest request) {
        return gameResultService.startSession(currentUser.id(authentication), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GameResultResponse complete(
            Authentication authentication, @Valid @RequestBody CompleteGameResultRequest request) {
        return gameResultService.complete(currentUser.id(authentication), request);
    }

    @GetMapping("/me")
    PageResponse<GameResultResponse> history(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return gameResultService.history(currentUser.id(authentication), page, size);
    }
}
