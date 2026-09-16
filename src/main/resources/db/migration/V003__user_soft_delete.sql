-- ========================================================================
-- Cancellazione logica degli utenti (self-service area account).
-- Alla cancellazione la riga resta come tombstone per l'integrità delle FK
-- (shows.created_by, audit_logs.user_id, ...), ma i dati personali vengono
-- anonimizzati e l'email originale liberata per un'eventuale reiscrizione.
-- ========================================================================

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_users_not_deleted ON users(deleted_at) WHERE deleted_at IS NULL;
