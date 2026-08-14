package com.gameio.dailychallenge;

import com.gameio.leaderboard.LeaderboardEntry;
import com.gameio.leaderboard.LeaderboardResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DailyChallengeQueryRepository {
    private static final String LEADERBOARD_SQL = """
            SELECT u.id, u.username, u.avatar_url, MAX(result.score) AS score
            FROM game_results result
            JOIN game_sessions session ON session.id = result.session_id
            JOIN users u ON u.id = result.player_id
            WHERE session.challenge_date = :challengeDate
            GROUP BY u.id, u.username, u.avatar_url, u.username_normalized
            ORDER BY score DESC, u.username_normalized ASC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    DailyChallengeQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<LocalDate> completedDates(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT session.challenge_date
                FROM game_results result
                JOIN game_sessions session ON session.id = result.session_id
                WHERE result.player_id = :userId AND session.challenge_date IS NOT NULL
                ORDER BY session.challenge_date DESC
                """, Map.of("userId", userId),
                (resultSet, rowNumber) -> resultSet.getObject(1, LocalDate.class));
    }

    long bestScore(UUID userId, LocalDate date) {
        Long score = jdbc.queryForObject("""
                SELECT COALESCE(MAX(result.score), 0)
                FROM game_results result
                JOIN game_sessions session ON session.id = result.session_id
                WHERE result.player_id = :userId AND session.challenge_date = :challengeDate
                """, Map.of("userId", userId, "challengeDate", date), Long.class);
        return score == null ? 0 : score;
    }

    long distinctSoloGames(UUID userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT result.game_id)
                FROM game_results result
                JOIN game_sessions session ON session.id = result.session_id
                WHERE result.player_id = :userId AND session.challenge_date IS NOT NULL
                """, Map.of("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    LeaderboardResponse leaderboard(LocalDate date, int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT result.player_id)
                FROM game_results result
                JOIN game_sessions session ON session.id = result.session_id
                WHERE session.challenge_date = :challengeDate
                """, Map.of("challengeDate", date), Long.class);
        Map<String, Object> parameters = Map.of(
                "challengeDate", date,
                "limit", size,
                "offset", (long) page * size);
        List<LeaderboardEntry> content = new ArrayList<>();
        List<Row> rows = jdbc.query(LEADERBOARD_SQL, parameters, (resultSet, rowNumber) -> new Row(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("avatar_url"),
                resultSet.getLong("score")));
        long firstRank = (long) page * size + 1;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            content.add(new LeaderboardEntry(firstRank + index, row.userId(), row.username(), row.avatarUrl(),
                    row.score(), 0));
        }
        long count = total == null ? 0 : total;
        int totalPages = count == 0 ? 0 : Math.toIntExact((count + size - 1) / size);
        return new LeaderboardResponse(List.copyOf(content), page, size, count, totalPages);
    }

    private record Row(UUID userId, String username, String avatarUrl, long score) {
    }
}
