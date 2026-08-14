package com.gameio.gameresult.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gameio.gameresult.replay.breakout.BreakoutEngine;
import com.gameio.gameresult.replay.memorymatch.MemoryMatchEngine;
import com.gameio.gameresult.replay.minesweeper.MinesweeperEngine;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class NewSoloGamesResultIntegrationTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void serverCreatesAndVerifiesBreakoutMinesweeperAndMemoryMatchRuns() throws Exception {
        String accessToken = registerAndAccessToken();

        Session breakout = startSession(accessToken, "breakout");
        Replay breakoutReplay = breakoutReplay(breakout.seed());
        complete(accessToken, breakout, breakoutReplay);

        Session minesweeper = startSession(accessToken, "minesweeper");
        Replay minesweeperReplay = minesweeperReplay(minesweeper.seed());
        complete(accessToken, minesweeper, minesweeperReplay);

        Session memoryMatch = startSession(accessToken, "memory-match");
        Replay memoryReplay = memoryReplay(memoryMatch.seed());
        complete(accessToken, memoryMatch, memoryReplay);
    }

    private String registerAndAccessToken() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .header("X-Gameio-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"NewSoloReplayPlayer","email":"new-solo-replay@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
    }

    private Session startSession(String accessToken, String gameSlug) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/game-results/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameSlug\":\"" + gameSlug + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSlug").value(gameSlug))
                .andReturn();
        String body = response.getResponse().getContentAsString();
        return new Session(
                gameSlug,
                JsonPath.read(body, "$.sessionId"),
                ((Number) JsonPath.read(body, "$.seed")).longValue());
    }

    private void complete(String accessToken, Session session, Replay replay) throws Exception {
        String actionsJson = replay.actions().stream()
                .map(action -> "\"" + action + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/game-results")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + session.sessionId() + "\",\"actions\":["
                                + actionsJson + "],\"durationSeconds\":" + replay.durationSeconds() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameSlug").value(session.gameSlug()))
                .andExpect(jsonPath("$.score").value(replay.score()));
    }

    private Replay breakoutReplay(long seed) {
        BreakoutEngine engine = new BreakoutEngine(seed);
        List<String> actions = new ArrayList<>();
        StringBuilder group = new StringBuilder(3);
        for (int tick = 0; tick < 50_000 && !engine.terminal(); tick++) {
            engine.step('L');
            group.append('L');
            if (group.length() == 3 || engine.terminal()) {
                actions.add(group.toString());
                group.setLength(0);
            }
        }
        assertThat(engine.terminal()).isTrue();
        return new Replay(actions, engine.state().score(), Math.max(1,
                (engine.state().tick() + BreakoutEngine.TICKS_PER_SECOND - 1)
                        / BreakoutEngine.TICKS_PER_SECOND));
    }

    private Replay minesweeperReplay(long seed) {
        MinesweeperEngine engine = new MinesweeperEngine(seed);
        List<String> actions = new ArrayList<>();
        for (int index = 0; index < 81 && !engine.terminal(); index++) {
            if (engine.reveal(index)) {
                actions.add("R:" + index);
            }
        }
        assertThat(engine.terminal()).isTrue();
        return new Replay(actions, engine.state().score(), Math.max(1, (actions.size() + 29) / 30));
    }

    private Replay memoryReplay(long seed) {
        int[] deck = new int[16];
        for (int value = 0; value < 8; value++) {
            deck[value * 2] = value;
            deck[value * 2 + 1] = value;
        }
        SeededRandom random = new SeededRandom(seed);
        for (int index = deck.length - 1; index > 0; index--) {
            int swapIndex = random.nextIndex(index + 1);
            int value = deck[index];
            deck[index] = deck[swapIndex];
            deck[swapIndex] = value;
        }
        Map<Integer, List<Integer>> positions = new HashMap<>();
        for (int index = 0; index < deck.length; index++) {
            positions.computeIfAbsent(deck[index], ignored -> new ArrayList<>()).add(index);
        }
        List<String> actions = new ArrayList<>();
        positions.values().stream().sorted((left, right) -> Integer.compare(left.get(0), right.get(0)))
                .forEach(pair -> {
                    actions.add("S:" + pair.get(0));
                    actions.add("S:" + pair.get(1));
                });
        MemoryMatchEngine engine = new MemoryMatchEngine(seed);
        actions.forEach(action -> engine.select(Integer.parseInt(action.substring(2))));
        assertThat(engine.terminal()).isTrue();
        return new Replay(actions, engine.state().score(), Math.max(1, (actions.size() + 29) / 30));
    }

    private record Session(String gameSlug, String sessionId, long seed) {
    }

    private record Replay(List<String> actions, long score, int durationSeconds) {
    }
}
