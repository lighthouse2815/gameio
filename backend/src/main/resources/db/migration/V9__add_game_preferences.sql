CREATE TABLE user_game_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    last_played_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_game_preferences_user_game UNIQUE (user_id, game_id)
);

CREATE INDEX idx_user_game_preferences_favorites
    ON user_game_preferences(user_id, favorite, updated_at DESC);
CREATE INDEX idx_user_game_preferences_recent
    ON user_game_preferences(user_id, last_played_at DESC);
