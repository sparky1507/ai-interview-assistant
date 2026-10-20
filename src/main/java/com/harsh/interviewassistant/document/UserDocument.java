package com.harsh.interviewassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A resume or job description owned by one user (table "documents", Flyway V3).
 * Named UserDocument to avoid clashing with Spring AI's Document (a chunk of text + metadata).
 *
 * The owner is referenced by id rather than a @ManyToOne User: ingestion and listing never need the
 * user's profile, so there's nothing to lazy-load and the aggregates stay independent.
 * Immutable after creation (no setters): re-uploading creates a new document.
 */
@Entity
@Table(name = "documents")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Null for pasted text. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;
}
