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
    '10000000-0000-0000-0000-000000000011',
    'Connect Four',
    'connect-four',
    'Drop discs into a live seven-column grid and connect four before your rival does.',
    NULL,
    'STRATEGY',
    'TURN_BASED_MULTIPLAYER',
    2,
    2,
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000012',
    'Reversi',
    'reversi',
    'Trap rival discs between your own, flip the board, and control the final territory.',
    NULL,
    'STRATEGY',
    'TURN_BASED_MULTIPLAYER',
    2,
    2,
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000013',
    'Rock Paper Scissors',
    'rock-paper-scissors',
    'Read your rival across hidden simultaneous rounds in a fast first-to-three duel.',
    NULL,
    'CASUAL',
    'TURN_BASED_MULTIPLAYER',
    2,
    2,
    TRUE,
    CURRENT_TIMESTAMP
);
