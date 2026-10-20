-- Step 3: uploaded resumes / job descriptions and their embedded chunks.

-- One row per uploaded or pasted document. The extracted text is kept so later steps can put the full
-- job description into a prompt without re-parsing the original file.
CREATE TABLE documents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    -- Detected from the bytes (not the client's header); 'text/plain' for pasted text
    content_type      VARCHAR(100) NOT NULL,
    -- NULL for pasted text (no file)
    size_bytes        BIGINT,
    content           TEXT         NOT NULL,
    char_count        INTEGER      NOT NULL,
    chunk_count       INTEGER      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_documents_type CHECK (type IN ('RESUME', 'JOB_DESCRIPTION'))
);

-- "My documents, newest first"
CREATE INDEX idx_documents_user_created ON documents (user_id, created_at DESC);

-- Spring AI PgVectorStore table. Column names/types are what PgVectorStore reads and writes
-- (validated at startup with schema-validation: true); the table name is set in application.yml.
-- metadata holds userId, documentId, documentType, chunkIndex: retrieval filters on userId so users
-- only ever search their own chunks.
CREATE TABLE document_chunks (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    -- 1024 dims: OpenAI text-embedding-3-small (dimensions=1024) or Ollama mxbai-embed-large.
    -- Changing the embedding model means a new migration AND re-embedding every document.
    embedding VECTOR(1024)
);

-- Approximate nearest-neighbour index for cosine distance (matches distance-type: COSINE_DISTANCE).
-- HNSW: better recall/speed than IVFFlat and needs no training data, so it works on an empty table.
CREATE INDEX idx_document_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
