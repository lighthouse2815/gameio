ALTER TABLE game_results ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE game_results ADD COLUMN match_id UUID;

ALTER TABLE game_results ADD CONSTRAINT chk_game_results_source
    CHECK (
        (session_id IS NOT NULL AND match_id IS NULL)
        OR (session_id IS NULL AND match_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_game_results_match_player
    ON game_results(match_id, player_id);
