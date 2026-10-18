package com.harsh.interviewassistant;

import org.junit.jupiter.api.Test;

/**
 * Boots the full app against a throwaway Postgres+pgvector container.
 * Passing means: config is valid, Flyway migrations run, and Hibernate's schema validation succeeds.
 */
class InterviewAssistantApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
