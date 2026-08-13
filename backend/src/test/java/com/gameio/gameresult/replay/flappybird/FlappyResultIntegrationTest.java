package com.gameio.gameresult.replay.flappybird;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
class FlappyResultIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlineRunPersistsOnlyTheScoreReplayedByTheServer() throws Exception {
        String accessToken = register();
        MvcResult started = mockMvc.perform(post("/api/game-results/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameSlug\":\"flappy-bird\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSlug").value("flappy-bird"))
                .andExpect(jsonPath("$.initialState.width").value(640))
                .andExpect(jsonPath("$.initialState.height").value(480))
                .andExpect(jsonPath("$.initialState.pipes.length()").value(3))
                .andExpect(jsonPath("$.initialState.status").value("playing"))
                .andReturn();
        String body = started.getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.sessionId");
        long seed = ((Number) JsonPath.read(body, "$.seed")).longValue();

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","actions":["WAIT"],"durationSeconds":1,"score":99999999}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","actions":["WAIT"],"durationSeconds":1}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GAME_ACTION"));

        List<String> actions = terminalScoringReplay(seed, 2);
        VerifiedReplay replay = new FlappyReplayVerifier().verify(seed, actions);
        String actionsJson = actions.stream().map(action -> "\"" + action + "\"")
                .collect(Collectors.joining(","));
        MvcResult completed = mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"actions\":[" + actionsJson
                                + "],\"durationSeconds\":" + replay.minimumDurationSeconds() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(replay.score()))
                .andExpect(jsonPath("$.result").value("COMPLETED"))
                .andReturn();
        String gameId = JsonPath.read(completed.getResponse().getContentAsString(), "$.gameId");

        mockMvc.perform(get("/api/game-results/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].gameName").value("Flappy Bird"))
                .andExpect(jsonPath("$.content[0].score").value(replay.score()));
        mockMvc.perform(get("/api/games/flappy-bird"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("SINGLE_PLAYER"))
                .andExpect(jsonPath("$.playsCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/games/{gameId}/leaderboard", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("FlappyReplayPlayer"))
                .andExpect(jsonPath("$.content[0].score").value(replay.score()));
    }

    private List<String> terminalScoringReplay(long seed, long targetScore) {
        FlappyEngine engine = new FlappyEngine(seed);
        List<String> actions = new ArrayList<>();
        while (!engine.terminal() && actions.size() < 1_000) {
            FlappyState state = engine.state();
            FlappyPipeState nextPipe = state.pipes().stream()
                    .filter(pipe -> pipe.x() + FlappyEngine.PIPE_WIDTH
                            >= state.birdX() - FlappyEngine.BIRD_HALF_WIDTH)
                    .findFirst()
                    .orElseThrow();
            boolean shouldFlap = state.score() < targetScore
                    && state.birdY() / 100 > nextPipe.gapCenter() - 20
                    && state.birdVelocity() > 50;
            FlappyAction action = shouldFlap ? FlappyAction.FLAP : FlappyAction.WAIT;
            engine.step(action);
            actions.add(action.name());
        }
        org.assertj.core.api.Assertions.assertThat(engine.terminal()).isTrue();
        return actions;
    }

    private String register() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"FlappyReplayPlayer","email":"flappy-replay@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }
}
