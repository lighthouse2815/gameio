INSERT INTO games (
    id,
    name,
    slug,
    description,
    thumbnail_url,
    category,
    game_type,
    min_players,
    max_players,
    enabled,
    created_at
) VALUES (
    '10000000-0000-0000-0000-000000000006',
    'Flappy Bird',
    'flappy-bird',
    'Thread a mechanical bird through shifting gates in offline practice or a server-verified ranked run.',
    NULL,
    'ARCADE',
    'SINGLE_PLAYER',
    1,
    1,
    TRUE,
    CURRENT_TIMESTAMP
);
