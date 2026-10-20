package com.harsh.interviewassistant.document;

/**
 * Keys of the JSON metadata stored with every chunk in document_chunks.
 * Retrieval (Step 4) filters on USER_ID so a search can only ever return the caller's own chunks.
 * Values are stored as strings so filter expressions compare like with like.
 */
public final class ChunkMetadata {

    public static final String USER_ID = "userId";
    public static final String DOCUMENT_ID = "documentId";
    public static final String DOCUMENT_TYPE = "documentType";
    public static final String CHUNK_INDEX = "chunkIndex";

    private ChunkMetadata() {
    }
}
