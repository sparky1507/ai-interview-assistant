package com.harsh.interviewassistant.document.dto;

import com.harsh.interviewassistant.document.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A pasted document, typically a job description copied from a job board. */
public record TextDocumentRequest(
        @NotNull DocumentType type,
        @NotBlank @Size(max = 255) String title,
        // Same cap as app.ingestion.max-extracted-chars, enforced again in the service for files
        @NotBlank @Size(max = 100_000) String content) {

    public TextDocumentRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.strip();
    }
}
