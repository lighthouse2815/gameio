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
) VALUES
(
    '10000000-0000-0000-0000-000000000007',
    'Breakout',
    'breakout',
    'Drive a precision paddle, keep the ball in play, and dismantle every brick in the wall.',
    NULL,
    'ARCADE',
    'SINGLE_PLAYER',
    1,
    1,
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000008',
    'Minesweeper',
    'minesweeper',
    'Map a compact minefield with careful reveals, flags, and a protected opening move.',
    NULL,
    'PUZZLE',
    'SINGLE_PLAYER',
    1,
    1,
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000009',
    'Memory Match',
    'memory-match',
    'Recover every hidden symbol pair with as few memory checks as possible.',
    NULL,
    'CASUAL',
    'SINGLE_PLAYER',
    1,
    1,
    TRUE,
    CURRENT_TIMESTAMP
);
