-- Store text embeddings for deep project-JD semantic matching (pgvector).
--
-- Mirrors V22's skill embeddings: dimension 256 matches the ML service's embedding
-- (model2vec potion-base-8M and the trigram fallback are both 256-dim), columns are nullable
-- (null = "not embedded yet"; lazy backfill fills them on first use), and the extension is
-- enabled out of band by a superuser.
--
-- students.projects_embedding embeds the student's recorded project text (names, summaries and
-- stacks); drives.description_embedding embeds the drive's role, description and required skills.
-- Their cosine (1 - <=>) is the deep project-JD relevance signal fed to the ranking model as the
-- project_relevance feature. Like the skill embeddings, these columns are intentionally not on the
-- JPA entities, so `ddl-auto: validate` ignores them; no index at demo scale.

ALTER TABLE students ADD COLUMN projects_embedding vector(256);
ALTER TABLE drives ADD COLUMN description_embedding vector(256);