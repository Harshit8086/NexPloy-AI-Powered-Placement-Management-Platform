-- Store skill embeddings for DB-side semantic similarity (pgvector).
--
-- The `vector` extension is enabled out of band by a superuser (a non-superuser role, which is
-- what Flyway runs as, cannot CREATE EXTENSION for an untrusted extension). This migration only
-- adds the columns, which the table owner (campuslink) may do. If the extension is somehow absent
-- the ALTERs fail loudly here rather than at query time — the intended fail-fast.
--
-- Dimension 256 matches the ML service's embedding (model2vec potion-base-8M and the trigram
-- fallback are both 256-dim). Columns are nullable: null means "not embedded yet" (lazy backfill
-- fills them on first use), which is distinct from a zero vector. Similarity is computed with the
-- cosine distance operator (<=>); no index is added at demo scale (a small candidate pool is an
-- exact scan), and an ivfflat/hnsw index can be added later when the pool grows.

ALTER TABLE students ADD COLUMN skills_embedding vector(256);
ALTER TABLE drives ADD COLUMN required_skills_embedding vector(256);
