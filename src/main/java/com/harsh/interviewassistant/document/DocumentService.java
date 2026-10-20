package com.harsh.interviewassistant.document;

import com.harsh.interviewassistant.common.exception.BadRequestException;
import com.harsh.interviewassistant.common.exception.ResourceNotFoundException;
import com.harsh.interviewassistant.document.dto.DocumentDetailResponse;
import com.harsh.interviewassistant.document.dto.DocumentResponse;
import com.harsh.interviewassistant.document.dto.TextDocumentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Ingestion pipeline: extract text -> split into chunks -> embed + store in pgvector.
 *
 * Synchronous and transactional: the documents row and its chunks commit together or not at all
 * (PgVectorStore writes through the same DataSource, so its JdbcTemplate joins the JPA transaction).
 * Trade-off: the embedding HTTP call runs inside the transaction and holds a DB connection for ~1s.
 * Fine for small documents; the upgrade path is async ingestion with a status column (PROCESSING/READY).
 */
@Slf4j
@Service
public class DocumentService {

    private static final int MAX_TITLE_LENGTH = 255;

    private final UserDocumentRepository repository;
    private final TextExtractor textExtractor;
    private final VectorStore vectorStore;
    private final IngestionProperties properties;
    private final TokenTextSplitter splitter;

    public DocumentService(UserDocumentRepository repository, TextExtractor textExtractor,
                           VectorStore vectorStore, IngestionProperties properties) {
        this.repository = repository;
        this.textExtractor = textExtractor;
        this.vectorStore = vectorStore;
        this.properties = properties;
        // Token-based (not character-based) so chunk size tracks what the embedding model actually counts
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSizeTokens())
                .withMinChunkSizeChars(200)      // prefer breaking at a sentence end after 200 chars
                .withMinChunkLengthToEmbed(10)   // drop near-empty fragments (stray bullets, page numbers)
                .withMaxNumChunks(1000)
                .withKeepSeparator(true)         // keep line breaks: resume structure carries meaning
                .build();
    }

    @Transactional
    public DocumentResponse ingestFile(UUID userId, DocumentType type, MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        String filename = safeFilename(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }

        TextExtractor.ExtractedText extracted = textExtractor.extract(bytes, filename);
        String effectiveTitle = StringUtils.hasText(title) ? title.strip() : filename;
        return DocumentResponse.from(
                store(userId, type, effectiveTitle, extracted.contentType(), (long) bytes.length, extracted.text()));
    }

    @Transactional
    public DocumentResponse ingestText(UUID userId, TextDocumentRequest request) {
        return DocumentResponse.from(
                store(userId, request.type(), request.title(), TextExtractor.TEXT, null, request.content()));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse get(UUID userId, UUID documentId) {
        return DocumentDetailResponse.from(find(userId, documentId));
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        UserDocument doc = find(userId, documentId);
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(ChunkMetadata.DOCUMENT_ID, doc.getId().toString())
                .build());
        repository.delete(doc);
    }

    private UserDocument store(UUID userId, DocumentType type, String title, String contentType,
                               Long sizeBytes, String text) {
        if (text.length() > properties.maxExtractedChars()) {
            throw new BadRequestException("Document is too long: %,d characters (max %,d)"
                    .formatted(text.length(), properties.maxExtractedChars()));
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }

        List<String> pieces = splitter.split(Document.builder().text(text).build()).stream()
                .map(Document::getText)
                .toList();
        if (pieces.isEmpty()) {
            throw new BadRequestException("The document has too little text to index");
        }

        // saveAndFlush: INSERT now so the generated id (needed in chunk metadata) and createdAt are set
        UserDocument doc = repository.saveAndFlush(UserDocument.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .content(text)
                .charCount(text.length())
                .chunkCount(pieces.size())
                .build());

        List<Document> chunks = IntStream.range(0, pieces.size())
                .mapToObj(i -> Document.builder()
                        .text(pieces.get(i))
                        .metadata(chunkMetadata(doc, i))
                        .build())
                .toList();

        // Embeds every chunk (batched by Spring AI) and inserts them; a failure here rolls back the row too
        vectorStore.add(chunks);
        log.info("Ingested {} '{}' for user {}: {} chars, {} chunks",
                type, doc.getTitle(), userId, doc.getCharCount(), chunks.size());
        return doc;
    }

    private static Map<String, Object> chunkMetadata(UserDocument doc, int index) {
        Map<String, Object> metadata = new HashMap<>(); // mutable: Spring AI may add keys (e.g. distance)
        metadata.put(ChunkMetadata.USER_ID, doc.getUserId().toString());
        metadata.put(ChunkMetadata.DOCUMENT_ID, doc.getId().toString());
        metadata.put(ChunkMetadata.DOCUMENT_TYPE, doc.getType().name());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        return metadata;
    }

    private UserDocument find(UUID userId, UUID documentId) {
        return repository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    /** Keep only the last path segment ("C:\\fakepath\\cv.pdf" -> "cv.pdf") and cap the length. */
    private static String safeFilename(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (!StringUtils.hasText(name)) {
            return "upload";
        }
        return name.length() > MAX_TITLE_LENGTH ? name.substring(0, MAX_TITLE_LENGTH) : name;
    }
}
