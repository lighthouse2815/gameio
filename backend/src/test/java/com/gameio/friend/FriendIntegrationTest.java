package com.gameio.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class FriendIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FriendshipRepository friendships;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void sendsListsAcceptsAndRemovesCanonicalFriendship() throws Exception {
        Player alice = register("FriendAlice", "friend-alice@example.com");
        Player bob = register("FriendBob", "friend-bob@example.com");

        MvcResult sendResult = authorizedPost(alice, "/api/friends/requests", """
                {"username":"friendbob"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender.id").value(alice.id().toString()))
                .andExpect(jsonPath("$.recipient.id").value(bob.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sender.online").value(false))
                .andExpect(jsonPath("$.recipient.online").value(false))
                .andReturn();
        String requestId = JsonPath.read(sendResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/friends/requests").header("Authorization", alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming.length()").value(0))
                .andExpect(jsonPath("$.outgoing.length()").value(1))
                .andExpect(jsonPath("$.outgoing[0].recipient.username").value("FriendBob"));
        mockMvc.perform(get("/api/friends/requests").header("Authorization", bob.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming.length()").value(1))
                .andExpect(jsonPath("$.incoming[0].sender.username").value("FriendAlice"))
                .andExpect(jsonPath("$.outgoing.length()").value(0));

        authorizedPost(alice, "/api/friends/requests/" + requestId + "/accept", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_INCOMING"));
        authorizedPost(bob, "/api/friends/requests/" + requestId + "/accept", null)
                .andExpect(status().isNoContent());
        authorizedPost(bob, "/api/friends/requests/" + requestId + "/accept", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));

        mockMvc.perform(get("/api/friends").header("Authorization", alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bob.id().toString()))
                .andExpect(jsonPath("$[0].username").value("FriendBob"))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].currentGameSlug").doesNotExist());
        mockMvc.perform(get("/api/friends").header("Authorization", bob.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("FriendAlice"));

        authorizedPost(bob, "/api/friends/requests", """
                {"username":"FRIENDALICE"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));

        mockMvc.perform(delete("/api/friends/fRiEnDaLiCe")
                        .header(CSRF_HEADER, "1")
                        .header("Authorization", bob.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/friends").header("Authorization", alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        Friendship.UserPair pair = Friendship.canonicalPair(alice.id(), bob.id());
        assertThat(friendships.findPair(pair.low(), pair.high())).isEmpty();
    }

    @Test
    void rejectsSelfDuplicateReverseAndUnauthorizedRequests() throws Exception {
        Player carol = register("FriendCarol", "friend-carol@example.com");
        Player dave = register("FriendDave", "friend-dave@example.com");
        Player observer = register("FriendObserver", "friend-observer@example.com");

        authorizedPost(carol, "/api/friends/requests", """
                {"username":"FriendCarol"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FRIEND_SELF"));
        authorizedPost(carol, "/api/friends/requests", """
                {"username":"MissingPlayer"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        MvcResult result = authorizedPost(carol, "/api/friends/requests", """
                {"username":"FriendDave"}
                """)
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        authorizedPost(carol, "/api/friends/requests", """
                {"username":"FriendDave"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_EXISTS"));
        authorizedPost(dave, "/api/friends/requests", """
                {"username":"FriendCarol"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_EXISTS"));

        authorizedPost(observer, "/api/friends/requests/" + requestId + "/accept", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));
        authorizedPost(dave, "/api/friends/requests/" + requestId + "/reject", null)
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/friends/requests").header("Authorization", carol.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming.length()").value(0))
                .andExpect(jsonPath("$.outgoing.length()").value(0));
        assertThat(friendships.count()).isZero();
    }

    @Test
    void validatesDtoPathAndAuthentication() throws Exception {
        Player erin = register("FriendErin", "friend-erin@example.com");

        authorizedPost(erin, "/api/friends/requests", """
                {"username":"invalid-name"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());
        mockMvc.perform(delete("/api/friends/invalid-name")
                        .header(CSRF_HEADER, "1")
                        .header("Authorization", erin.authorization()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        authorizedPost(erin, "/api/friends/requests/not-a-uuid/accept", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private org.springframework.test.web.servlet.ResultActions authorizedPost(
            Player player, String path, String body) throws Exception {
        var request = post(path)
                .header(CSRF_HEADER, "1")
                .header("Authorization", player.authorization());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
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
        return new Player(
                username,
                UUID.fromString(JsonPath.read(response, "$.user.id")),
                JsonPath.read(response, "$.accessToken"));
    }

    private record Player(String username, UUID id, String accessToken) {
        String authorization() {
            return "Bearer " + accessToken;
        }
    }
}
