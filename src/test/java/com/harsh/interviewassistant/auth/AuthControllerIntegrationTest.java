package com.harsh.interviewassistant.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.harsh.interviewassistant.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow through the real filter chain, controllers and Postgres.
 * Each test uses its own random email, so tests stay independent while sharing one database.
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "s3cret-pass";

    @Test
    void registerThenLoginThenGetCurrentUser() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, PASSWORD, "Jane Doe")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.createdAt", notNullValue()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String token = login(email, PASSWORD);

        String meBody = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        assertThat(meBody).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        String email = uniqueEmail();
        register("  " + email.toUpperCase() + "  ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email)); // stored normalized

        assertThat(login(email.toUpperCase(), PASSWORD)).isNotBlank();
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();

        // Same title/detail/status: the response doesn't reveal whether the account exists
        JsonNode a = objectMapper.readTree(wrongPassword);
        JsonNode b = objectMapper.readTree(unknownEmail);
        assertThat(a.get("title")).isEqualTo(b.get("title"));
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrors() throws Exception {
        register("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()))
                .andExpect(jsonPath("$.errors.fullName", notNullValue()));
    }

    @Test
    void malformedJsonReturns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void currentUserWithoutTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/me"));
    }

    @Test
    void currentUserWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid-token"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private ResultActions register(String email, String password) throws Exception {
        return register(email, password, "Test User");
    }

    private ResultActions register(String email, String password, String fullName)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, password, fullName)));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String registerJson(String email, String password, String fullName) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email, "password", password, "fullName", fullName));
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
