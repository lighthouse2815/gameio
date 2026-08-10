package com.gameio.game;

import com.gameio.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameQueryService gameQueryService;

    public GameController(GameQueryService gameQueryService) {
        this.gameQueryService = gameQueryService;
    }

    @GetMapping
    PageResponse<GameResponse> search(
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) GameCategory category,
            @RequestParam(required = false) GameType gameType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return gameQueryService.search(search, category, gameType, page, size);
    }

    @GetMapping("/{slug}")
    GameResponse findBySlug(
            @PathVariable @Pattern(regexp = "[a-z0-9-]{2,80}") String slug) {
        return gameQueryService.findBySlug(slug);
    }
}
