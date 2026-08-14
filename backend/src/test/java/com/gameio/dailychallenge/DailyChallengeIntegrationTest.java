package com.gameio.dailychallenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class DailyChallengeIntegrationTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void exposesPublicMetadataAndRankingButRequiresIdentityForSessionAndProgress() throws Exception {
        MvcResult today = mockMvc.perform(get("/api/daily-challenges/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameSlug").isNotEmpty())
                .andExpect(jsonPath("$.startsAt").isNotEmpty())
                .andExpect(jsonPath("$.endsAt").isNotEmpty())
                .andReturn();
        String body = today.getResponse().getContentAsString();
        String date = JsonPath.read(body, "$.date");
        String gameSlug = JsonPath.read(body, "$.gameSlug");

        mockMvc.perform(get("/api/daily-challenges/" + date + "/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(post("/api/daily-challenges/today/sessions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/daily-challenges/me"))
                .andExpect(status().isUnauthorized());

        String accessToken = registerAndAccessToken();
        MvcResult first = startSession(accessToken, date, gameSlug);
        MvcResult second = startSession(accessToken, date, gameSlug);
        Number firstSeed = JsonPath.read(first.getResponse().getContentAsString(), "$.seed");
        Number secondSeed = JsonPath.read(second.getResponse().getContentAsString(), "$.seed");
        assertThat(secondSeed.longValue()).isEqualTo(firstSeed.longValue());

        mockMvc.perform(get("/api/daily-challenges/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedToday").value(false))
                .andExpect(jsonPath("$.currentStreak").value(0));
    }

    private MvcResult startSession(String accessToken, String date, String gameSlug) throws Exception {
        return mockMvc.perform(post("/api/daily-challenges/today/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSlug").value(gameSlug))
                .andExpect(jsonPath("$.challengeDate").value(date))
                .andReturn();
    }

    private String registerAndAccessToken() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header("X-Gameio-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"DailyChallengePlayer","email":"daily-challenge@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }
}
