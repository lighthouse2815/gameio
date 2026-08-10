INSERT INTO games (id, name, slug, description, thumbnail_url, category, game_type, min_players, max_players, enabled, created_at) VALUES
('10000000-0000-0000-0000-000000000001', '2048', '2048', 'Combine matching tiles and build the highest value before the board fills.', NULL, 'PUZZLE', 'SINGLE_PLAYER', 1, 1, TRUE, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000002', 'Snake', 'snake', 'Guide a growing snake, collect food, and survive at increasing speed.', NULL, 'ARCADE', 'SINGLE_PLAYER', 1, 1, TRUE, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000003', 'Tic Tac Toe', 'tic-tac-toe', 'A quick server-authoritative strategy match for two players.', NULL, 'STRATEGY', 'TURN_BASED_MULTIPLAYER', 2, 2, TRUE, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000004', 'Caro', 'caro', 'Place five pieces in a row on a 15 by 15 board before your opponent.', NULL, 'STRATEGY', 'TURN_BASED_MULTIPLAYER', 2, 2, TRUE, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000005', 'Tank Battle', 'tank-battle', 'Pilot a tank in a compact real-time arena for two to four players.', NULL, 'ACTION', 'REALTIME_MULTIPLAYER', 2, 4, TRUE, CURRENT_TIMESTAMP);

INSERT INTO achievements (id, code, name, description, icon, exp_reward) VALUES
('20000000-0000-0000-0000-000000000001', 'FIRST_GAME', 'First Steps', 'Complete your first verified game.', 'gamepad-2', 25),
('20000000-0000-0000-0000-000000000002', 'FIRST_WIN', 'First Victory', 'Win your first verified multiplayer match.', 'trophy', 40),
('20000000-0000-0000-0000-000000000003', 'PLAY_10_GAMES', 'Regular Player', 'Complete ten verified games.', 'medal', 75),
('20000000-0000-0000-0000-000000000004', 'WIN_10_GAMES', 'Ten Victories', 'Win ten verified multiplayer matches.', 'crown', 120),
('20000000-0000-0000-0000-000000000005', 'SCORE_1000_SNAKE', 'Snake Specialist', 'Reach a verified score of 1000 in Snake.', 'cherry', 100),
('20000000-0000-0000-0000-000000000006', 'WIN_5_TICTACTOE', 'Grid Master', 'Win five verified Tic Tac Toe matches.', 'grid-3x3', 90);
