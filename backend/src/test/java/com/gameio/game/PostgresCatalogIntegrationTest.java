package com.gameio.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresCatalogIntegrationTest {
    private static final String JWT_SECRET =
            "postgres-integration-secret-with-more-than-thirty-two-characters";
    private static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SNAKE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("gameio")
            .withUsername("gameio")
            .withPassword("gameio_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void productionProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("REDIS_URL", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        seedVerifiedPlay();
    }

    @Test
    void productionCatalogWorksWithAbsentSearchAndReportsPostgresPlayCount() throws Exception {
        MvcResult catalog = mockMvc.perform(get("/api/games").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andReturn();

        List<Map<String, Object>> snakeEntries = JsonPath.read(
                catalog.getResponse().getContentAsString(), "$.content[?(@.slug == 'snake')]");
        assertThat(snakeEntries).singleElement().satisfies(snake -> {
            assertThat(snake.get("playsCount")).isEqualTo(1);
            assertThat(snake.get("onlinePlayers")).isEqualTo(0);
        });

        mockMvc.perform(get("/api/games").param("search", " snake "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("snake"))
                .andExpect(jsonPath("$.content[0].playsCount").value(1));
    }

    private void seedVerifiedPlay() {
        jdbc.update("""
                insert into users (
                    id, username, username_normalized, email, email_normalized, password_hash,
                    role, level, exp, created_at, updated_at, entity_version
                ) values (?, 'PostgresPlayer', 'postgresplayer', 'postgres@example.com',
                    'postgres@example.com', 'not-used-in-this-test', 'USER', 1, 0,
                    current_timestamp, current_timestamp, 0)
                """, PLAYER_ID);
        jdbc.update("""
                insert into game_results (
                    id, session_id, game_id, player_id, score, result,
                    duration_seconds, played_at, match_id
                ) values (?, null, ?, ?, 10, 'COMPLETED', 1, current_timestamp, ?)
                """, UUID.randomUUID(), SNAKE_ID, PLAYER_ID, UUID.randomUUID());
    }
}
