package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthIntegrationTest.GoogleVerifierTestConfiguration.class)
class AuthIntegrationTest {
    private static final String CSRF_HEADER = "X-Gameio-CSRF";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void currentProfileRequiresAuthenticationBeforePublicUsernameRoute() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void registerStoresRefreshTokenOnlyInHttpOnlyCookieAndAuthenticatesAccessToken() throws Exception {
        MvcResult registration = register("CookiePlayer", "cookie-player@example.com");

        Cookie refreshCookie = registration.getResponse().getCookie("gameio_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(registration.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
        assertThat(registration.getResponse().getContentAsString()).doesNotContain("refreshToken");

        String accessToken = JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("CookiePlayer"))
                .andExpect(jsonPath("$.email").value("cookie-player@example.com"));
    }

    @Test
    void rotatesCookieDetectsReuseAndRevokesTheWholeTokenFamily() throws Exception {
        MvcResult registration = register("RotatePlayer", "rotate-player@example.com");
        Cookie original = registration.getResponse().getCookie("gameio_refresh");

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .header(CSRF_HEADER, "1")
                        .cookie(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        Cookie rotated = refresh.getResponse().getCookie("gameio_refresh");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        mockMvc.perform(post("/api/auth/refresh").header(CSRF_HEADER, "1").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        mockMvc.perform(post("/api/auth/refresh").header(CSRF_HEADER, "1").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void rejectsMissingCsrfHeaderAndLogoutClearsCookie() throws Exception {
        MvcResult registration = register("LogoutPlayer", "logout-player@example.com");
        Cookie refresh = registration.getResponse().getCookie("gameio_refresh");

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_REQUIRED"));

        mockMvc.perform(post("/api/auth/logout").header(CSRF_HEADER, "1").cookie(refresh))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mockMvc.perform(post("/api/auth/refresh").header(CSRF_HEADER, "1").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameAndEmailAreRejected() throws Exception {
        register("DuplicatePlayer", "duplicate-player@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"duplicateplayer","email":"another@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void oversizedUtf8LoginPasswordReturnsControlledInvalidCredentials() throws Exception {
        register("ByteLimitPlayer", "byte-limit@example.com");
        String oversizedPassword = "😀".repeat(20);

        mockMvc.perform(post("/api/auth/login")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ByteLimitPlayer","password":"%s"}
                                """.formatted(oversizedPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void googleSignInCreatesProviderOnlyAccountAndReusesItByStableSubject() throws Exception {
        MvcResult first = googleLogin("new-google-player")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("new-google-player@gmail.com"))
                .andExpect(jsonPath("$.user.username").value("new_google_player"))
                .andReturn();
        Cookie refreshCookie = first.getResponse().getCookie("gameio_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        String firstUserId = JsonPath.read(first.getResponse().getContentAsString(), "$.user.id");

        googleLogin("returning-google-player-changed-email")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(firstUserId))
                .andExpect(jsonPath("$.user.email").value("new-google-player@gmail.com"));

        mockMvc.perform(post("/api/auth/login")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"new-google-player@gmail.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void googleSignInNeverAutoLinksAnExistingPasswordAccountByEmail() throws Exception {
        register("GoogleLinked", "google-linked@gmail.com");
        googleLogin("link-existing-gmail")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_LINK_REQUIRED"));
    }

    @Test
    void googleSignInDoesNotAutoLinkExistingThirdPartyEmail() throws Exception {
        register("ExternalEmail", "external-link@example.com");

        googleLogin("third-party-existing-email")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_LINK_REQUIRED"));
    }

    @Test
    void googleSignInGeneratesBoundedDeterministicUsernameOnCollision() throws Exception {
        register("collisionplayer", "collision-owner@example.com");

        MvcResult first = googleLogin("username-collision")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(
                        org.hamcrest.Matchers.matchesPattern("collisionplayer_[a-f0-9]{6}")))
                .andReturn();
        String generatedUsername = JsonPath.read(first.getResponse().getContentAsString(), "$.user.username");
        assertThat(generatedUsername).hasSizeLessThanOrEqualTo(24);

        googleLogin("username-collision")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(generatedUsername));
    }

    @Test
    void googleEndpointValidatesCredentialAndReturnsControlledTokenFailure() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.idToken").exists());

        googleLogin("invalid-google-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_ID_TOKEN"));
    }

    private org.springframework.test.web.servlet.ResultActions googleLogin(String credential) throws Exception {
        return mockMvc.perform(post("/api/auth/google")
                .header(CSRF_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idToken":"%s"}
                        """.formatted(credential)));
    }

    private MvcResult register(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .header(CSRF_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"StrongPassword123!"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GoogleVerifierTestConfiguration {
        @Bean
        @Primary
        GoogleIdTokenVerifier testGoogleIdTokenVerifier() {
            return credential -> switch (credential) {
                case "new-google-player" -> new VerifiedGoogleIdentity(
                        "google-subject-new", "new-google-player@gmail.com");
                case "returning-google-player-changed-email" -> new VerifiedGoogleIdentity(
                        "google-subject-new", "changed-google-address@gmail.com");
                case "link-existing-gmail" -> new VerifiedGoogleIdentity(
                        "google-subject-linked", "google-linked@gmail.com");
                case "third-party-existing-email" -> new VerifiedGoogleIdentity(
                        "google-subject-third-party", "external-link@example.com");
                case "username-collision" -> new VerifiedGoogleIdentity(
                        "google-subject-collision", "collisionplayer@gmail.com");
                default -> throw new com.gameio.common.error.UnauthorizedException(
                        "INVALID_GOOGLE_ID_TOKEN", "Google ID token is invalid");
            };
        }
    }
}
