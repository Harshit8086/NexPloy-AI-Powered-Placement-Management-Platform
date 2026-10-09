package com.nexploy.campuslink.matching.client;

import java.util.List;

/**
 * Boundary to the Python ML service for skill embeddings. Only the ML service holds the embedding
 * model, so Spring never computes vectors itself — it asks for one and stores it (pgvector). The
 * returned vector is the same one the service uses for in-service similarity, so cosine distance
 * over stored vectors matches it.
 */
public interface EmbeddingClient {

    /** The embedding for a set of skill terms, or {@code null} when no terms were supplied. */
    List<Double> embed(List<String> terms);
}
