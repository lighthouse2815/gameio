package com.gameio.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gameio.game.Game;
import com.gameio.game.GameRepository;
import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativeMatchResult;
import com.gameio.gameresult.multiplayer.AuthoritativePlayerOutcome;
import com.gameio.gameresult.multiplayer.AuthoritativeResultService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompetitionIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired private WebApplicationContext context;
    @Autowired private GameRepository games;
    @Autowired private AuthoritativeResultService results;
    @Autowired private TournamentService tournaments;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recordsSeasonEloAndRunsSingleEliminationTournament() throws Exception {
        Player alpha = register("ArenaAlpha", "arena-alpha@example.com");
        Player beta = register("ArenaBeta", "arena-beta@example.com");
        Game game = games.findBySlugAndEnabledTrue("tic-tac-toe").orElseThrow();

        var progression = results.record(new AuthoritativeMatchResult(
                UUID.randomUUID(), game.getId(), 42,
                List.of(
                        new AuthoritativePlayerOutcome(alpha.id(), GameResultType.WIN, 1),
                        new AuthoritativePlayerOutcome(beta.id(), GameResultType.LOSS, 0))));

        assertThat(progression).extracting(item -> item.ratingAfter()).containsExactly(1016, 984);
        mockMvc.perform(get("/api/competition/season"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.startsAt").isString())
                .andExpect(jsonPath("$.endsAt").isString());
        mockMvc.perform(get("/api/competition/ratings").param("gameId", game.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].username").value(alpha.username()))
                .andExpect(jsonPath("$.content[0].rating").value(1016))
                .andExpect(jsonPath("$.content[0].rank").value(1))
                .andExpect(jsonPath("$.content[1].username").value(beta.username()))
                .andExpect(jsonPath("$.content[1].rating").value(984));
        mockMvc.perform(get("/api/competition/ratings/me").header("Authorization", alpha.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameSlug").value("tic-tac-toe"))
                .andExpect(jsonPath("$[0].rating").value(1016));

        MvcResult created = authorizedPost(alpha, "/api/competition/tournaments", """
                {"name":"Launch Cup","gameId":"%s","maxPlayers":"4"}
                """.formatted(game.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tournament.status").value("REGISTRATION"))
                .andExpect(jsonPath("$.tournament.joinedPlayers").value(1))
                .andReturn();
        UUID tournamentId = UUID.fromString(JsonPath.read(
                created.getResponse().getContentAsString(), "$.tournament.id"));

        authorizedPost(beta, "/api/competition/tournaments/" + tournamentId + "/join", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2));
        MvcResult started = authorizedPost(alpha,
                "/api/competition/tournaments/" + tournamentId + "/start", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournament.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.tournament.currentRound").value(1))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.matches[0].roomId").isString())
                .andReturn();
        UUID roomId = UUID.fromString(JsonPath.read(
                started.getResponse().getContentAsString(), "$.matches[0].roomId"));

        tournaments.recordMatchResult(roomId, List.of(
                new AuthoritativePlayerOutcome(alpha.id(), GameResultType.WIN, 1),
                new AuthoritativePlayerOutcome(beta.id(), GameResultType.LOSS, 0)));

        mockMvc.perform(get("/api/competition/tournaments/" + tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournament.status").value("COMPLETED"))
                .andExpect(jsonPath("$.tournament.winnerUsername").value(alpha.username()))
                .andExpect(jsonPath("$.matches[0].winnerUsername").value(alpha.username()))
                .andExpect(jsonPath("$.players[1].eliminated").value(true));
    }

    @Test
    void protectsTournamentWritesAndValidatesBracketCapacity() throws Exception {
        Player creator = register("ArenaOwner", "arena-owner@example.com");
        Game game = games.findBySlugAndEnabledTrue("tic-tac-toe").orElseThrow();

        mockMvc.perform(post("/api/competition/tournaments")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Auth Cup","gameId":"%s","maxPlayers":"4"}
                                """.formatted(game.getId())))
                .andExpect(status().isUnauthorized());
        authorizedPost(creator, "/api/competition/tournaments", """
                {"name":"Bad Capacity","gameId":"%s","maxPlayers":"5"}
                """.formatted(game.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.maxPlayers").exists());
    }

    private org.springframework.test.web.servlet.ResultActions authorizedPost(
            Player player, String path, String body) throws Exception {
        var request = post(path)
                .header(CSRF_HEADER, "1")
                .header("Authorization", player.authorization());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private Player register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"StrongPassword123!"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        return new Player(username, UUID.fromString(JsonPath.read(response, "$.user.id")),
                JsonPath.read(response, "$.accessToken"));
    }

    private record Player(String username, UUID id, String accessToken) {
        String authorization() { return "Bearer " + accessToken; }
    }
}
