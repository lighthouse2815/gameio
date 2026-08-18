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
    '10000000-0000-0000-0000-000000000010',
    'Type Rush',
    'typing-race',
    'Build ten-finger speed in local practice, then race a rival in a server-authoritative typing duel.',
    NULL,
    'CASUAL',
    'REALTIME_MULTIPLAYER',
    2,
    2,
    TRUE,
    CURRENT_TIMESTAMP
);
