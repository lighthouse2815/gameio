package com.gameio.stats;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PlayerStatsIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void analyticsContainOnlyServerAcceptedResultsAndZeroFilledActivity() throws Exception {
        String accessToken = register();
        MvcResult started = mockMvc.perform(post("/api/game-results/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameSlug\":\"snake\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = JsonPath.read(started.getResponse().getContentAsString(), "$.sessionId");
        String actions = Collections.nCopies(10, "\"RIGHT\"").stream().collect(Collectors.joining(","));

        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"actions\":[" + actions
                                + "],\"durationSeconds\":2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.gamesPlayed").value(1))
                .andExpect(jsonPath("$.summary.completed").value(1))
                .andExpect(jsonPath("$.summary.activeDays").value(1))
                .andExpect(jsonPath("$.summary.currentPlayStreak").value(1))
                .andExpect(jsonPath("$.games[0].gameSlug").value("snake"))
                .andExpect(jsonPath("$.games[0].gamesPlayed").value(1))
                .andExpect(jsonPath("$.activity.length()").value(30))
                .andExpect(jsonPath("$.activity[29].gamesPlayed").value(1))
                .andExpect(jsonPath("$.achievements.unlocked").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mostPlayedGameSlug").value("snake"));
    }

    private String register() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"StatsPlayer","email":"stats-player@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }
}
