package com.gameio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gameio.gameresult.replay.game2048.Game2048Engine;
import com.gameio.gameresult.replay.game2048.Game2048State;
import com.gameio.gameresult.replay.game2048.MoveDirection;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class CoreApiIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void seededCatalogIsPublicSearchableAndContainsThirteenRealGames() throws Exception {
        mockMvc.perform(get("/api/games").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.content[?(@.slug == '2048')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'flappy-bird')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'breakout')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'minesweeper')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'memory-match')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'typing-race')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'connect-four')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'reversi')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'rock-paper-scissors')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'tank-battle')]").exists());
    }

    @Test
    void resultScoreIsDerivedFromServerReplayAndFlowsToProfileLeaderboardAndAchievement() throws Exception {
        String accessToken = registerAndAccessToken();
        MvcResult sessionResult = mockMvc.perform(post("/api/game-results/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameSlug\":\"2048\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.initialState.board").isArray())
                .andReturn();

        String sessionBody = sessionResult.getResponse().getContentAsString();
        String sessionId = JsonPath.read(sessionBody, "$.sessionId");
        long seed = ((Number) JsonPath.read(sessionBody, "$.seed")).longValue();

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","actions":["LEFT"],"durationSeconds":1,"score":99999999}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        Replay replay = playUntilGameOver(seed);
        int duration = Math.max(1, (replay.actions().size() + 29) / 30);
        assertThat(duration).isLessThanOrEqualTo(30);
        String actionsJson = replay.actions().stream().map(action -> "\"" + action + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        MvcResult completion = mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"actions\":[" + actionsJson
                                + "],\"durationSeconds\":" + duration + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(replay.state().score()))
                .andExpect(jsonPath("$.result").value("COMPLETED"))
                .andExpect(jsonPath("$.expAwarded").value(35))
                .andExpect(jsonPath("$.unlockedAchievements[0].code").value("FIRST_GAME"))
                .andReturn();
        String gameId = JsonPath.read(completion.getResponse().getContentAsString(), "$.gameId");

        mockMvc.perform(get("/api/game-results/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].gameName").value("2048"))
                .andExpect(jsonPath("$.content[0].score").value(replay.state().score()));
        mockMvc.perform(get("/api/users/ReplayPlayer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamesPlayed").value(1))
                .andExpect(jsonPath("$.achievements[0].code").value("FIRST_GAME"));
        mockMvc.perform(get("/api/games/{gameId}/leaderboard", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("ReplayPlayer"))
                .andExpect(jsonPath("$.content[0].score").value(replay.state().score()));
        mockMvc.perform(get("/api/leaderboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("ReplayPlayer"));
    }

    private String registerAndAccessToken() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ReplayPlayer","email":"replay-player@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }

    private Replay playUntilGameOver(long seed) {
        Game2048Engine engine = new Game2048Engine(seed);
        MoveDirection[] pattern = {
                MoveDirection.LEFT, MoveDirection.UP, MoveDirection.RIGHT, MoveDirection.DOWN
        };
        List<String> actions = new ArrayList<>();
        for (int step = 0; step < 10_000 && !engine.state().gameOver(); step++) {
            MoveDirection direction = pattern[step % pattern.length];
            engine.move(direction);
            actions.add(direction.name());
        }
        Game2048State state = engine.state();
        assertThat(state.gameOver()).as("deterministic replay should reach game over").isTrue();
        return new Replay(List.copyOf(actions), state);
    }

    private record Replay(List<String> actions, Game2048State state) {
    }
}
