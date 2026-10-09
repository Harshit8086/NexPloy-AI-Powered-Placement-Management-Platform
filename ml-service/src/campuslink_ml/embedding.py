"""Semantic-similarity embedding for candidate matching.

Primary path: a trained static embedding model via model2vec (`minishlab/potion-base-8M`),
distilled from a sentence-transformer. It is a real learned model — it knows that unrelated skill
sets are dissimilar and related ones are close — yet inference is pure numpy (no torch, no GPU, no
network at inference once the model is cached), and it is deterministic (same input → same vector).

Fallback path: if the model can't be loaded (not downloaded and offline, or a blocked native
dependency), we degrade to a deterministic char-trigram hashed-vector cosine so the matching
pipeline still returns a value rather than failing. Both paths expose the same
`semantic_similarity_pct(required_skills, candidate_skills)` contract, so nothing downstream changes.

Swapping models (a larger potion model, a domain-tuned one, or an external embeddings API) is a
change to `_load_model` / `_model_vector` only.
"""

import hashlib
import os

import numpy as np

# Silence the Windows symlink cache warning from huggingface_hub; caching still works.
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

_MODEL_NAME = os.environ.get("EMBEDDING_MODEL", "minishlab/potion-base-8M")
_DIM = 256  # char-trigram fallback dimension

# Lazily-loaded singleton: None = not tried yet, False = load failed (use fallback), else the model.
_model: object | None | bool = None


def _load_model():
    global _model
    if _model is None:
        try:
            from model2vec import StaticModel

            _model = StaticModel.from_pretrained(_MODEL_NAME)
        except Exception:  # noqa: BLE001 — any failure (offline, missing, blocked) → deterministic fallback
            _model = False
    return _model


def _normalize(vec: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vec)
    return vec / norm if norm > 0 else vec


def _trigram_vector(text: str) -> np.ndarray:
    """Deterministic char-trigram hashed vector (the offline fallback embedder)."""
    vec = np.zeros(_DIM, dtype=np.float64)
    normalized = "".join(ch.lower() if ch.isalnum() else " " for ch in text)
    tokens = normalized.split()
    if not tokens:
        return vec
    padded = f"  {' '.join(tokens)}  "
    for i in range(len(padded) - 2):
        trigram = padded[i : i + 3]
        bucket = int(hashlib.md5(trigram.encode("utf-8")).hexdigest(), 16) % _DIM
        vec[bucket] += 1.0
    return _normalize(vec)


def _model_vector(text: str) -> np.ndarray | None:
    model = _load_model()
    if not model:
        return None
    vector = np.asarray(model.encode([text])[0], dtype=np.float64)
    return _normalize(vector)


def embed_terms(terms: list[str]) -> list[float] | None:
    """The L2-normalized embedding for a set of skill terms, as a plain float list for storage
    (e.g. a pgvector column). Returns None when there are no terms — missing evidence, not a zero
    vector. Uses the trained model when available, else the deterministic trigram fallback. This is
    the *same* vector `semantic_similarity_pct` compares, so cosine distance computed over stored
    vectors (1 - dist) matches this module's in-service similarity."""
    if not terms:
        return None
    text = " ".join(terms)
    vec = _model_vector(text)
    if vec is None:
        vec = _trigram_vector(text)
    return vec.tolist()


def embedding_dim() -> int:
    """Dimension of the vectors `embed_terms` produces. The model and the trigram fallback are both
    256-dim, so the pgvector column width is fixed regardless of which path is active."""
    return _DIM


def semantic_similarity_pct(required_skills: list[str], candidate_skills: list[str]) -> float | None:
    """Cosine similarity (0-100) between the drive's required skills and a candidate's skills.
    Returns None when either side has no skill terms — missing evidence, not zero similarity.
    Uses the trained model when available, else the deterministic trigram fallback."""
    if not required_skills or not candidate_skills:
        return None

    required_text = " ".join(required_skills)
    candidate_text = " ".join(candidate_skills)

    required_vec = _model_vector(required_text)
    candidate_vec = _model_vector(candidate_text)
    if required_vec is None or candidate_vec is None:
        required_vec = _trigram_vector(required_text)
        candidate_vec = _trigram_vector(candidate_text)

    similarity = float(np.dot(required_vec, candidate_vec))  # both L2-normalized
    return round(max(0.0, min(1.0, similarity)) * 100.0, 2)
