package com.gameio.gameresult.replay.snake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SnakeResultIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void snakeSessionRejectsUnverifiedPayloadsAndPersistsOnlyServerReplayScore() throws Exception {
        String accessToken = register();
        MvcResult started = mockMvc.perform(post("/api/game-results/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameSlug\":\"snake\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSlug").value("snake"))
                .andExpect(jsonPath("$.initialState.width").value(20))
                .andExpect(jsonPath("$.initialState.height").value(15))
                .andExpect(jsonPath("$.initialState.body.length()").value(3))
                .andExpect(jsonPath("$.initialState.direction").value("right"))
                .andExpect(jsonPath("$.initialState.status").value("playing"))
                .andReturn();
        String body = started.getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.sessionId");
        long seed = ((Number) JsonPath.read(body, "$.seed")).longValue();

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","actions":["RIGHT"],"durationSeconds":1,"score":99999999}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","actions":["RIGHT"],"durationSeconds":1}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GAME_ACTION"));

        String trailingActions = Collections.nCopies(11, "\"RIGHT\"").stream()
                .collect(Collectors.joining(","));
        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"actions\":[" + trailingActions
                                + "],\"durationSeconds\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GAME_ACTION"));

        java.util.List<String> actions = Collections.nCopies(10, "RIGHT");
        long expectedScore = new SnakeReplayVerifier().verify(seed, actions).score();
        String actionsJson = actions.stream().map(action -> "\"" + action + "\"")
                .collect(Collectors.joining(","));
        MvcResult completed = mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"actions\":[" + actionsJson
                                + "],\"durationSeconds\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(expectedScore))
                .andExpect(jsonPath("$.result").value("COMPLETED"))
                .andReturn();
        String gameId = JsonPath.read(completed.getResponse().getContentAsString(), "$.gameId");

        mockMvc.perform(get("/api/game-results/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].gameName").value("Snake"))
                .andExpect(jsonPath("$.content[0].score").value(expectedScore));
        mockMvc.perform(get("/api/games/snake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playsCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.onlinePlayers").isNumber());
        mockMvc.perform(get("/api/games/{gameId}/leaderboard", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("SnakeReplayPlayer"))
                .andExpect(jsonPath("$.content[0].score").value(expectedScore));
    }

    private String register() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"SnakeReplayPlayer","email":"snake-replay@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }
}
