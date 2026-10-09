import math

from campuslink_ml.embedding import embed_terms, embedding_dim, semantic_similarity_pct


def test_embed_terms_is_normalized_and_fixed_dim() -> None:
    vec = embed_terms(["Java", "Spring Boot"])
    assert vec is not None
    assert len(vec) == embedding_dim() == 256
    # L2-normalized: unit length (both the trained model and the trigram fallback normalize).
    assert math.isclose(math.sqrt(sum(v * v for v in vec)), 1.0, rel_tol=1e-6)


def test_embed_terms_none_for_no_terms() -> None:
    # No skills is missing evidence, not a zero vector.
    assert embed_terms([]) is None


def test_stored_vector_cosine_matches_in_service_similarity() -> None:
    # The vector embed_terms stores must reproduce semantic_similarity_pct when compared by cosine,
    # so DB-side `1 - (a <=> b)` (pgvector) equals the number this module computes in-service.
    required = ["Java", "Spring Boot", "REST"]
    candidate = ["Java", "Spring", "Microservices"]

    a = embed_terms(required)
    b = embed_terms(candidate)
    cosine = sum(x * y for x, y in zip(a, b))  # both unit vectors → dot product is cosine
    db_side_pct = round(max(0.0, min(1.0, cosine)) * 100.0, 2)

    assert math.isclose(db_side_pct, semantic_similarity_pct(required, candidate), abs_tol=0.01)
