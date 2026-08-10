package com.gameio.leaderboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class LeaderboardQueryRepository {
    private static final String GLOBAL_SQL = """
            SELECT u.id, u.username, u.avatar_url, u.exp AS score,
                   COALESCE(SUM(CASE WHEN result.result = 'WIN' THEN 1 ELSE 0 END), 0) AS wins
            FROM users u
            LEFT JOIN game_results result ON result.player_id = u.id
            GROUP BY u.id, u.username, u.avatar_url, u.exp, u.username_normalized
            ORDER BY u.exp DESC, wins DESC, u.username_normalized ASC
            LIMIT :limit OFFSET :offset
            """;

    private static final String GAME_SQL = """
            SELECT u.id, u.username, u.avatar_url, MAX(result.score) AS score,
                   COALESCE(SUM(CASE WHEN result.result = 'WIN' THEN 1 ELSE 0 END), 0) AS wins
            FROM game_results result
            JOIN users u ON u.id = result.player_id
            WHERE result.game_id = :gameId
            GROUP BY u.id, u.username, u.avatar_url, u.username_normalized
            ORDER BY score DESC, wins DESC, u.username_normalized ASC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    LeaderboardQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    LeaderboardResponse global(int page, int size) {
        long total = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return query(GLOBAL_SQL, Map.of("limit", size, "offset", (long) page * size), page, size, total);
    }

    LeaderboardResponse forGame(UUID gameId, int page, int size) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT player_id) FROM game_results WHERE game_id = :gameId",
                Map.of("gameId", gameId), Long.class);
        return query(GAME_SQL, Map.of("gameId", gameId, "limit", size, "offset", (long) page * size),
                page, size, total == null ? 0 : total);
    }

    private LeaderboardResponse query(
            String sql, Map<String, ?> parameters, int page, int size, long total) {
        long firstRank = (long) page * size + 1;
        List<Row> rows = jdbc.query(sql, parameters, (resultSet, rowNumber) -> new Row(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("avatar_url"),
                resultSet.getLong("score"),
                resultSet.getLong("wins")));
        List<LeaderboardEntry> content = java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> {
                    Row row = rows.get(index);
                    return new LeaderboardEntry(firstRank + index, row.userId(), row.username(), row.avatarUrl(),
                            row.score(), row.wins());
                }).toList();
        int totalPages = total == 0 ? 0 : Math.toIntExact((total + size - 1) / size);
        return new LeaderboardResponse(content, page, size, total, totalPages);
    }

    private record Row(UUID userId, String username, String avatarUrl, long score, long wins) {
    }
}
