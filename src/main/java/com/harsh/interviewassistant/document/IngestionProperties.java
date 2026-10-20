package com.harsh.interviewassistant.document;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binding of app.ingestion.*.
 *
 * @param chunkSizeTokens   target chunk size for the token splitter
 * @param maxExtractedChars documents with more extracted text are rejected (bounds embedding cost)
 */
@Validated
@ConfigurationProperties("app.ingestion")
public record IngestionProperties(
        @Positive int chunkSizeTokens,
        @Positive int maxExtractedChars) {
}
