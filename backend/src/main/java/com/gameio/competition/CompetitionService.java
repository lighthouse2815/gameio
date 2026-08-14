package com.gameio.competition;

import com.gameio.common.web.PageResponse;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompetitionService {
    private final SeasonService seasons;
    private final SeasonRatingRepository ratings;
    private final GameRepository games;

    CompetitionService(SeasonService seasons, SeasonRatingRepository ratings, GameRepository games) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.games = games;
    }

    @Transactional
    SeasonResponse currentSeason() {
        return SeasonResponse.from(seasons.current());
    }

    @Transactional
    PageResponse<RatingEntryResponse> leaderboard(UUID gameId, int page, int size) {
        games.findById(gameId).filter(com.gameio.game.Game::isEnabled).orElseThrow(GameNotFoundException::new);
        Season season = seasons.current();
        Page<SeasonRating> result = ratings.findBySeasonIdAndGameIdOrderByRatingDescGamesPlayedDescUpdatedAtAsc(
                season.id(), gameId, PageRequest.of(page, size));
        long offset = (long) page * size;
        List<RatingEntryResponse> content = java.util.stream.IntStream.range(0, result.getContent().size())
                .mapToObj(index -> RatingEntryResponse.from(result.getContent().get(index), offset + index + 1))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    List<RatingEntryResponse> mine(UUID userId) {
        Season season = seasons.current();
        return ratings.findBySeasonIdAndUserIdOrderByRatingDesc(season.id(), userId).stream()
                .map(rating -> RatingEntryResponse.from(rating, 0))
                .toList();
    }
}
