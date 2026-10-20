package com.harsh.interviewassistant.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic, offline stand-in for OpenAI/Ollama in tests: no API key, no network, no cost.
 *
 * Hashes each word into one of 1024 buckets and L2-normalizes the counts ("hashing trick"), so texts that
 * share words get similar vectors: similarity search still behaves sensibly in retrieval tests.
 * Text containing FAIL_EMBEDDING throws, to simulate a provider outage.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1024;
    public static final String FAIL_MARKER = "FAIL_EMBEDDING";

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
        if (text.contains(FAIL_MARKER)) {
            // What Spring AI's OpenAI/Ollama clients throw for e.g. HTTP 429 insufficient_quota
            throw new NonTransientAiException("Simulated embedding provider failure");
        }
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f; // pgvector can't compute cosine distance for an all-zero vector
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public int callCount() {
        return calls.get();
    }
}
