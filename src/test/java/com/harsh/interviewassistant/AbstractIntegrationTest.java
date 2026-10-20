package com.harsh.interviewassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.interviewassistant.support.FakeEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that boot the full app against a real Postgres+pgvector.
 *
 * Singleton container pattern: one container, started once per JVM in a static initializer and shared
 * by every subclass (instead of @Testcontainers/@Container, which would restart it per test class).
 * Identical config in every subclass also lets Spring reuse one cached application context.
 * Testcontainers' Ryuk sidecar removes the container when the JVM exits.
 *
 * AI providers are switched off and replaced by {@link FakeEmbeddingModel}: tests never need an API key.
 */
@SpringBootTest(properties = {
        "spring.ai.model.embedding=none",
        "spring.ai.model.chat=none"
})
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.FakeAiConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfig {

        @Bean
        FakeEmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }
    }

    /** Registers a fresh user and returns their access token. */
    protected String registerUserAndGetToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "user-" + UUID.randomUUID() + "@example.com",
                "password", "s3cret-pass",
                "fullName", "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
