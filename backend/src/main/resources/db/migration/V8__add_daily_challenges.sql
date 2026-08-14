ALTER TABLE game_sessions ADD COLUMN challenge_date DATE;

CREATE INDEX idx_game_sessions_challenge_date
    ON game_sessions(challenge_date, game_id, player_id);

INSERT INTO achievements (id, code, name, description, icon, exp_reward) VALUES
('20000000-0000-0000-0000-000000000007', 'DAILY_FIRST', 'Daily Signal', 'Complete your first verified Daily Challenge.', 'calendar-check', 40),
('20000000-0000-0000-0000-000000000008', 'DAILY_STREAK_3', 'Three Day Link', 'Complete a Daily Challenge on three consecutive days.', 'flame', 90),
('20000000-0000-0000-0000-000000000009', 'DAILY_ALL_SOLO', 'Daily Operator', 'Complete Daily Challenges across all six solo game engines.', 'orbit', 180);
