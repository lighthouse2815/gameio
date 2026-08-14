CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_seasons_code UNIQUE (code),
    CONSTRAINT chk_seasons_window CHECK (ends_at > starts_at)
);

CREATE TABLE season_ratings (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL DEFAULT 1000 CHECK (rating >= 0),
    games_played INTEGER NOT NULL DEFAULT 0 CHECK (games_played >= 0),
    wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    losses INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
    draws INTEGER NOT NULL DEFAULT 0 CHECK (draws >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_season_ratings_scope UNIQUE (season_id, user_id, game_id)
);

CREATE INDEX idx_season_ratings_rank
    ON season_ratings(season_id, game_id, rating DESC, games_played DESC);

CREATE TABLE tournaments (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    game_id UUID NOT NULL REFERENCES games(id),
    created_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(24) NOT NULL,
    max_players INTEGER NOT NULL CHECK (max_players IN (4, 8, 16)),
    current_round INTEGER NOT NULL DEFAULT 0 CHECK (current_round >= 0),
    winner_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    entity_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tournaments_status_created ON tournaments(status, created_at DESC);

CREATE TABLE tournament_entries (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seed_number INTEGER NOT NULL CHECK (seed_number >= 1),
    eliminated BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_tournament_entries_player UNIQUE (tournament_id, user_id),
    CONSTRAINT uk_tournament_entries_seed UNIQUE (tournament_id, seed_number)
);

CREATE TABLE tournament_matches (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL CHECK (round_number >= 1),
    bracket_index INTEGER NOT NULL CHECK (bracket_index >= 0),
    player_one_id UUID NOT NULL REFERENCES users(id),
    player_two_id UUID REFERENCES users(id),
    winner_id UUID REFERENCES users(id),
    room_id UUID,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_tournament_match_slot UNIQUE (tournament_id, round_number, bracket_index),
    CONSTRAINT uk_tournament_match_room UNIQUE (room_id)
);

CREATE INDEX idx_tournament_matches_round
    ON tournament_matches(tournament_id, round_number, bracket_index);
