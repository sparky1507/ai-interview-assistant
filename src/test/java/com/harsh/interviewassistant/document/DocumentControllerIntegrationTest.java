package com.harsh.interviewassistant.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.harsh.interviewassistant.AbstractIntegrationTest;
import com.harsh.interviewassistant.support.FakeEmbeddingModel;
import com.harsh.interviewassistant.support.TestFiles;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload -> Tika extraction -> chunking -> (fake) embedding -> pgvector, through the real HTTP layer.
 * Assertions also look directly at document_chunks, since that's what retrieval in Step 4 will read.
 */
class DocumentControllerIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> RESUME_LINES = List.of(
            "Jane Doe - Senior Backend Engineer",
            "Experience: 6 years building Java and Spring Boot microservices.",
            "Designed a PostgreSQL schema and Kafka event pipeline for payments.",
            "Skills: Java 21, Spring Security, Docker, Kubernetes, AWS.");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void uploadPdfResumeStoresChunksScopedToTheUser() throws Exception {
        String token = registerUserAndGetToken();
        String userId = currentUserId(token);

        JsonNode doc = json(upload(token, "resume.pdf", TestFiles.pdf(RESUME_LINES), "RESUME")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESUME"))
                .andExpect(jsonPath("$.title").value("resume.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.chunkCount", greaterThan(0)))
                .andExpect(jsonPath("$.createdAt").exists()));

        String documentId = doc.get("id").asText();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT content, metadata::jsonb ->> 'userId' AS user_id, metadata::jsonb ->> 'documentType' AS type "
                        + "FROM document_chunks WHERE metadata::jsonb ->> 'documentId' = ?", documentId);

        assertThat(rows).hasSize(doc.get("chunkCount").asInt());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("user_id")).isEqualTo(userId);
            assertThat(row.get("type")).isEqualTo("RESUME");
        });
        assertThat(rows).extracting(row -> (String) row.get("content"))
                .anySatisfy(content -> assertThat(content).contains("Spring Boot microservices"));
    }

    @Test
    void uploadDocxAndReadBackExtractedText() throws Exception {
        String token = registerUserAndGetToken();

        String id = json(upload(token, "cv.docx", TestFiles.docx(RESUME_LINES), "RESUME")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value(TextExtractor.DOCX)))
                .get("id").asText();

        mockMvc.perform(get("/api/v1/documents/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.id").value(id))
                .andExpect(jsonPath("$.content", containsString("Kafka event pipeline")));
    }

    @Test
    void pastedJobDescriptionIsSplitIntoOrderedChunks() throws Exception {
        String token = registerUserAndGetToken();
        // ~2,500 words: several 400-token chunks
        String longJd = IntStream.range(0, 250)
                .mapToObj(i -> "Requirement " + i + ": experience with distributed systems, observability and on-call.")
                .reduce("", (a, b) -> a + b + "\n");

        JsonNode doc = json(mockMvc.perform(post("/api/v1/documents/text")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "JOB_DESCRIPTION", "title", "  Platform Engineer @ Acme  ", "content", longJd))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Platform Engineer @ Acme"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").doesNotExist()));

        int chunkCount = doc.get("chunkCount").asInt();
        assertThat(chunkCount).isGreaterThan(1);
        List<Integer> indexes = jdbcTemplate.queryForList(
                "SELECT (metadata::jsonb ->> 'chunkIndex')::int FROM document_chunks "
                        + "WHERE metadata::jsonb ->> 'documentId' = ? ORDER BY 1", Integer.class, doc.get("id").asText());
        assertThat(indexes).containsExactlyElementsOf(IntStream.range(0, chunkCount).boxed().toList());
    }

    @Test
    void renamedExecutableIsRejectedByContentNotFilename() throws Exception {
        String token = registerUserAndGetToken();
        byte[] fakeExe = "MZ\u0090\u0000\u0003 this is not a PDF".getBytes();

        upload(token, "resume.pdf", fakeExe, "RESUME")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail", containsString("Only PDF, DOCX and TXT")));
    }

    @Test
    void pdfWithoutTextLayerIsRejected() throws Exception {
        String token = registerUserAndGetToken();

        upload(token, "scan.pdf", TestFiles.pdf(List.of()), "RESUME")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("No text could be extracted")));
    }

    @Test
    void emptyFileAndUnknownTypeAreBadRequests() throws Exception {
        String token = registerUserAndGetToken();

        upload(token, "empty.pdf", new byte[0], "RESUME").andExpect(status().isBadRequest());
        upload(token, "resume.pdf", TestFiles.pdf(RESUME_LINES), "COVER_LETTER").andExpect(status().isBadRequest());
    }

    @Test
    void usersCannotSeeOrDeleteEachOthersDocuments() throws Exception {
        String owner = registerUserAndGetToken();
        String intruder = registerUserAndGetToken();
        String id = json(upload(owner, "resume.pdf", TestFiles.pdf(RESUME_LINES), "RESUME")
                .andExpect(status().isCreated())).get("id").asText();

        // 404 rather than 403: don't reveal that the document exists
        mockMvc.perform(get("/api/v1/documents/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/documents/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void similaritySearchFilteredByUserOnlyReturnsThatUsersChunks() throws Exception {
        String alice = registerUserAndGetToken();
        String bob = registerUserAndGetToken();
        upload(alice, "alice.pdf", TestFiles.pdf(RESUME_LINES), "RESUME").andExpect(status().isCreated());
        upload(bob, "bob.pdf", TestFiles.pdf(RESUME_LINES), "RESUME").andExpect(status().isCreated());
        String aliceId = currentUserId(alice);

        // This is the query shape Step 4's retrieval will use
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Spring Boot microservices experience")
                .topK(10)
                .filterExpression(new FilterExpressionBuilder().eq(ChunkMetadata.USER_ID, aliceId).build())
                .build());

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(d -> assertThat(d.getMetadata().get(ChunkMetadata.USER_ID)).isEqualTo(aliceId));
    }

    @Test
    void deleteRemovesTheDocumentAndItsEmbeddings() throws Exception {
        String token = registerUserAndGetToken();
        String id = json(upload(token, "resume.pdf", TestFiles.pdf(RESUME_LINES), "RESUME")
                .andExpect(status().isCreated())).get("id").asText();
        assertThat(chunkRows(id)).isPositive();

        mockMvc.perform(delete("/api/v1/documents/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(chunkRows(id)).isZero();
        mockMvc.perform(get("/api/v1/documents/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void embeddingProviderFailureReturns503AndRollsBackTheDocumentRow() throws Exception {
        String token = registerUserAndGetToken();
        String title = "rollback-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/documents/text")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "JOB_DESCRIPTION", "title", title,
                                "content", "We need a Java developer. " + FakeEmbeddingModel.FAIL_MARKER))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI provider unavailable"));

        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM documents WHERE title = ?", Integer.class, title);
        assertThat(rows).isZero();
    }

    @Test
    void documentsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private ResultActions upload(String token, String filename, byte[] bytes, String type) throws Exception {
        // Client-declared content type is deliberately generic: the server must detect the real type itself
        MockMultipartFile file = new MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes);
        return mockMvc.perform(multipart("/api/v1/documents").file(file).param("type", type)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String currentUserId(String token) throws Exception {
        return json(mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token))))
                .get("id").asText();
    }

    private int chunkRows(String documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE metadata::jsonb ->> 'documentId' = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
