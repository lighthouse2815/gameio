CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(24) NOT NULL,
    username_normalized VARCHAR(24) NOT NULL,
    email VARCHAR(254) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    level INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1),
    exp BIGINT NOT NULL DEFAULT 0 CHECK (exp >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_username_normalized UNIQUE (username_normalized),
    CONSTRAINT uk_users_email_normalized UNIQUE (email_normalized)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_hash VARCHAR(64),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);

CREATE TABLE games (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    thumbnail_url VARCHAR(500),
    category VARCHAR(30) NOT NULL,
    game_type VARCHAR(40) NOT NULL,
    min_players INTEGER NOT NULL CHECK (min_players >= 1),
    max_players INTEGER NOT NULL CHECK (max_players >= min_players),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_games_slug UNIQUE (slug)
);

CREATE INDEX idx_games_enabled_category ON games(enabled, category);

CREATE TABLE achievements (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon VARCHAR(80) NOT NULL,
    exp_reward INTEGER NOT NULL CHECK (exp_reward >= 0),
    CONSTRAINT uk_achievements_code UNIQUE (code)
);

CREATE TABLE player_achievements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id UUID NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, achievement_id)
);

CREATE INDEX idx_player_achievements_unlocked ON player_achievements(user_id, unlocked_at DESC);

CREATE TABLE game_sessions (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id),
    player_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    random_seed BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    entity_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_game_sessions_player_status ON game_sessions(player_id, status);
CREATE INDEX idx_game_sessions_expiry ON game_sessions(expires_at);

CREATE TABLE game_results (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    game_id UUID NOT NULL REFERENCES games(id),
    player_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score BIGINT NOT NULL CHECK (score >= 0),
    result VARCHAR(20) NOT NULL,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
    played_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_game_results_session UNIQUE (session_id)
);

CREATE INDEX idx_game_results_game_score ON game_results(game_id, score DESC, played_at ASC);
CREATE INDEX idx_game_results_player_played ON game_results(player_id, played_at DESC);
CREATE INDEX idx_game_results_player_result ON game_results(player_id, result);
