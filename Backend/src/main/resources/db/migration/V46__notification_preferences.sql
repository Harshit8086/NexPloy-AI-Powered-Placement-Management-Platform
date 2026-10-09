-- Step 4 of the multi-channel notification system: per-recipient notification preferences.
--
-- Opt-out model: a row exists only when a user has explicitly set a choice for a type; the absence
-- of a row means that type is enabled (the default). The event consumer (NotificationEventListener
-- → NotificationService.createFromEvent) skips creating a notification whose type the recipient has
-- disabled. Self-scoped: a user only ever reads/writes rows keyed by their own username.
-- (In the H2 test profile the schema comes from the JPA entities, so this migration is Postgres-only.)

CREATE TABLE IF NOT EXISTS notification_preferences (
    id          VARCHAR(64)  PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_notification_pref_user_type UNIQUE (username, type)
);

-- Enforcement + listing both look preferences up by user.
CREATE INDEX IF NOT EXISTS idx_notification_pref_username
    ON notification_preferences (username);
