-- ========================================================================
-- Vincoli e indici dall'audit del database (referto audit-database-2026-09-26, sezioni 2, 3,
-- 4 e problemi 9, 10, 14, 16). Numerata prima di V011 ma scritta dopo: non tocca nessuna delle
-- colonne che V011 toglie (niente CHECK su documents.visibility, niente NOT NULL su di essa).
-- Violazioni misurate in produzione il 26/09: 0 per tutto, tranne calendar_events id 2 (sotto).
-- ========================================================================

-- ------------------------------------------------------------------------
-- Correzione dati (decisione di Federico del 26/09)
-- ------------------------------------------------------------------------

-- Problema 9: la riga id 2 (gia' cancellata, fine 25/09 16:00 prima dell'inizio 26/09 08:00)
-- bloccherebbe calendar_events_end_after_start. Fine = inizio. Senza effetto su un db vuoto.
UPDATE calendar_events SET end_datetime = start_datetime
 WHERE id = 2 AND end_datetime < start_datetime;

-- ------------------------------------------------------------------------
-- CHECK (sezione 2)
-- ------------------------------------------------------------------------

-- Sezione 2: fine evento calendario non prima dell'inizio (CalendarService).
ALTER TABLE calendar_events ADD CONSTRAINT calendar_events_end_after_start
    CHECK (end_datetime IS NULL OR end_datetime >= start_datetime);
-- Sezione 2: apertura prenotazioni prima della chiusura (EventService).
ALTER TABLE events ADD CONSTRAINT events_booking_open_before_close
    CHECK (booking_open_at IS NULL OR booking_close_at IS NULL OR booking_open_at < booking_close_at);
-- Sezione 2: chiusura prenotazioni non dopo l'evento (EventService).
ALTER TABLE events ADD CONSTRAINT events_booking_close_before_event
    CHECK (booking_close_at IS NULL OR booking_close_at <= event_date);
-- Sezione 2: tipo di file, enum MediaFileType.
ALTER TABLE documents ADD CONSTRAINT documents_media_type_values
    CHECK (media_type IN ('DOCUMENT', 'AUDIO', 'VIDEO'));
-- Sezione 2: dimensione e contatore non negativi.
ALTER TABLE documents ADD CONSTRAINT documents_counts_positive
    CHECK (file_size_bytes >= 0 AND download_count >= 0);
-- Sezione 2: tipo di token, le 2 costanti di Token.
ALTER TABLE tokens ADD CONSTRAINT tokens_type_values
    CHECK (token_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'));
-- Sezione 2: priorita' della comunicazione (valori del commento di V001).
ALTER TABLE communications ADD CONSTRAINT communications_priority_values
    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
-- Sezione 2: scadenza della comunicazione dopo la pubblicazione.
ALTER TABLE communications ADD CONSTRAINT communications_expires_after_published
    CHECK (expires_at IS NULL OR published_at IS NULL OR expires_at > published_at);
-- Sezione 2, problema 10: email sempre in minuscolo.
ALTER TABLE users ADD CONSTRAINT users_email_lowercase
    CHECK (email = lower(email));
-- Sezione 2: provider OAuth e suo id sempre insieme.
ALTER TABLE users ADD CONSTRAINT users_oauth_both_or_none
    CHECK ((oauth_provider IS NULL) = (oauth_id IS NULL));
-- Sezione 2: colore del tipo evento nella forma #RRGGBB.
ALTER TABLE calendar_event_types ADD CONSTRAINT calendar_event_types_color_hex
    CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$');
-- Sezione 2: provincia di 2 lettere maiuscole.
ALTER TABLE events ADD CONSTRAINT events_province_format
    CHECK (location_province IS NULL OR location_province ~ '^[A-Z]{2}$');
-- Sezione 2, problema 8: lista di ruoli = nomi maiuscoli separati da virgola, senza spazi.
ALTER TABLE calendar_events ADD CONSTRAINT calendar_events_target_roles_format
    CHECK (target_roles IS NULL OR target_roles ~ '^[A-Z_]+(,[A-Z_]+)*$');
-- Sezione 2, problema 8: stessa regola per le comunicazioni.
ALTER TABLE communications ADD CONSTRAINT communications_target_roles_format
    CHECK (target_roles IS NULL OR target_roles ~ '^[A-Z_]+(,[A-Z_]+)*$');
-- Sezione 2: durata ed eta' consigliata non negative.
ALTER TABLE shows ADD CONSTRAINT shows_non_negative
    CHECK (duration_minutes >= 0 AND (age_recommendation IS NULL OR age_recommendation >= 0));

-- ------------------------------------------------------------------------
-- NOT NULL sulle colonne con default (problema 14; documents.visibility la toglie V011)
-- ------------------------------------------------------------------------

-- Problema 14: tokens.used.
ALTER TABLE tokens ALTER COLUMN used SET NOT NULL;
-- Problema 14: communications.pinned.
ALTER TABLE communications ALTER COLUMN pinned SET NOT NULL;
-- Problema 14: communications.priority.
ALTER TABLE communications ALTER COLUMN priority SET NOT NULL;
-- Problema 14: documents.download_count.
ALTER TABLE documents ALTER COLUMN download_count SET NOT NULL;
-- Problema 14: show_images.display_order.
ALTER TABLE show_images ALTER COLUMN display_order SET NOT NULL;

-- ------------------------------------------------------------------------
-- Indici da togliere (sezione 3, "Da togliere")
-- ------------------------------------------------------------------------

-- Sezione 3: doppione di users_email_key.
DROP INDEX idx_users_email;
-- Sezione 3: doppione di documents_uuid_key.
DROP INDEX idx_documents_uuid;
-- Sezione 3: ricerca a testo pieno mai usata.
DROP INDEX idx_documents_fts;
-- Sezione 3: nessuna query filtra per users.active.
DROP INDEX idx_users_active;
-- Sezione 3: la pulizia filtra solo per scadenza, sostituito da I12.
DROP INDEX idx_tokens_type_expires;
-- Sezione 3: sostituiti da I5.
DROP INDEX idx_calendar_start;
DROP INDEX idx_calendar_not_deleted;
-- Sezione 3: sostituiti da I7.
DROP INDEX idx_events_date;
DROP INDEX idx_events_not_deleted;
-- Sezione 3: sostituiti da I8.
DROP INDEX idx_comms_published;
DROP INDEX idx_comms_not_deleted;
-- Sezione 3: sostituito da I10.
DROP INDEX idx_users_not_deleted;
-- Sezione 3: sostituito da I3.
DROP INDEX idx_documents_folder;
-- Sezione 3: indicizzava solo NULL, sostituito da I9.
DROP INDEX idx_shows_not_deleted;
-- Sezione 3: indicizzava solo NULL, sostituito da I4.
DROP INDEX idx_documents_not_deleted;

-- ------------------------------------------------------------------------
-- Indici da aggiungere (sezione 3, I1..I12 e chiavi esterne)
-- ------------------------------------------------------------------------

-- I1: galleria dello spettacolo e cascata da shows.
CREATE INDEX idx_show_images_show ON show_images(show_id);
-- I2: sottocartelle attive (cancellazione ricorsiva).
CREATE INDEX idx_folders_parent_active ON folders(parent_folder_id) WHERE deleted_at IS NULL;
-- I3: documenti attivi di una cartella.
CREATE INDEX idx_documents_folder_active ON documents(folder_id) WHERE deleted_at IS NULL;
-- I4: albero dei documenti ordinato per titolo.
CREATE INDEX idx_documents_title_active ON documents(title) WHERE deleted_at IS NULL;
-- I5: mese del calendario.
CREATE INDEX idx_calendar_start_active ON calendar_events(start_datetime) WHERE deleted_at IS NULL;
-- I6: existsByEventTypeId, conta anche le cancellate (non parziale).
CREATE INDEX idx_calendar_events_type ON calendar_events(event_type_id);
-- I7: eventi pubblici per data.
CREATE INDEX idx_events_date_active ON events(event_date) WHERE deleted_at IS NULL;
-- I8: bacheca delle comunicazioni.
CREATE INDEX idx_comms_board ON communications(pinned DESC, published_at DESC) WHERE deleted_at IS NULL;
-- I9: lista pubblica degli spettacoli.
CREATE INDEX idx_shows_year_active ON shows(production_year DESC) WHERE deleted_at IS NULL;
-- I10: lista utenti in admin.
CREATE INDEX idx_users_created_active ON users(created_at DESC) WHERE deleted_at IS NULL;
-- I11, problema 10: email unica ignorando le maiuscole (Spring Data ...IgnoreCase usa upper()).
CREATE UNIQUE INDEX ux_users_email_upper ON users(upper(email));
-- I12: pulizia dei token scaduti.
CREATE INDEX idx_tokens_expires ON tokens(expires_at);
-- Sezione 3, chiave esterna events.show_id.
CREATE INDEX idx_events_show ON events(show_id);
-- Sezione 3, chiave esterna events.event_type_id.
CREATE INDEX idx_events_type ON events(event_type_id);
-- Sezione 3, chiave esterna communications.communication_type_id.
CREATE INDEX idx_comms_type ON communications(communication_type_id);

-- Sezione 4 punto 6: nome di cartella unico (senza maiuscole) nello stesso padre, radice compresa.
CREATE UNIQUE INDEX ux_folders_name_in_parent
    ON folders(COALESCE(parent_folder_id, 0), lower(name)) WHERE deleted_at IS NULL;
