package com.harsh.interviewassistant;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that boot the full app against a real Postgres+pgvector.
 *
 * Singleton container pattern: one container, started once per JVM in a static initializer and shared
 * by every subclass (instead of @Testcontainers/@Container, which would restart it per test class).
 * Identical config in every subclass also lets Spring reuse one cached application context.
 * Testcontainers' Ryuk sidecar removes the container when the JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }
}
