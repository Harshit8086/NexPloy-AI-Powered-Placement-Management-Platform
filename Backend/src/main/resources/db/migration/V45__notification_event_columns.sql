-- Step 1 of the multi-channel notification system: make notifications event-driven.
--
-- Adds the columns the event consumer (NotificationEventListener) populates:
--   * source_event_id — the DomainEvent that produced this notification; the idempotency key so a
--                       replayed event never duplicates (paired with recipient_username below).
--   * type           — the semantic NotificationType (OFFER_EXTENDED, INTERVIEW_SCHEDULED, …),
--                       used for client deep-linking and per-category preference filtering.
--   * reference_id   — id of the domain entity the notification is about (offer/interview/…),
--                       paired with type to build the deep link.
-- All three are nullable: pre-existing rows and ad-hoc officer-created notifications have none.
-- (In the H2 test profile the schema comes from the JPA entities, so this migration is Postgres-only.)

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(64);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type VARCHAR(40);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_id VARCHAR(64);

-- Idempotency lookup: at most one notification per (source event, recipient).
CREATE INDEX IF NOT EXISTS idx_notifications_source_event
    ON notifications (source_event_id, recipient_username);

-- Powers the unread-count badge and mark-all-read sweep (recipient's unread notifications).
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_status
    ON notifications (recipient_username, status);
