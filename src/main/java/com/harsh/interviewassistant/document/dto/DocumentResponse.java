package com.harsh.interviewassistant.document.dto;

import com.harsh.interviewassistant.document.DocumentType;
import com.harsh.interviewassistant.document.UserDocument;

import java.time.Instant;
import java.util.UUID;

/** Summary of a document; the extracted text is only returned by the detail endpoint. */
public record DocumentResponse(
        UUID id,
        DocumentType type,
        String title,
        String contentType,
        Long sizeBytes,
        int charCount,
        int chunkCount,
        Instant createdAt) {

    public static DocumentResponse from(UserDocument doc) {
        return new DocumentResponse(doc.getId(), doc.getType(), doc.getTitle(), doc.getContentType(),
                doc.getSizeBytes(), doc.getCharCount(), doc.getChunkCount(), doc.getCreatedAt());
    }
}
