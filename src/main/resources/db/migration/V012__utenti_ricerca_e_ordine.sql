-- ========================================================================
-- Lista utenti in admin: ordine dei ruoli e ricerca per nome/email senza accenti.
-- Interfaccia fissata da god (28/09): roles.sort_order, public.abc_fold(text),
-- due indici GIN trigram parziali su users. La query del backend deve usare
-- ESATTAMENTE le espressioni degli indici, altrimenti il planner non li usa.
-- ========================================================================

-- ------------------------------------------------------------------------
-- Ordine dei ruoli (decisione di Federico: chi gestisce, poi produzione, soci, iscritti)
-- ------------------------------------------------------------------------

ALTER TABLE roles ADD COLUMN sort_order INT;

UPDATE roles SET sort_order = CASE name
    WHEN 'GOD'        THEN 10
    WHEN 'ADMIN'      THEN 20
    WHEN 'STAFF'      THEN 30
    WHEN 'DIRECTOR'   THEN 40
    WHEN 'TECHNICIAN' THEN 50
    WHEN 'MEMBER'     THEN 60
    WHEN 'REGISTER'   THEN 70
    WHEN 'PUBLIC'     THEN 80
END;

-- Un ruolo non previsto non riceve un valore inventato: la migrazione si ferma.
DO $$
DECLARE
    unknown TEXT;
BEGIN
    SELECT string_agg(name, ', ' ORDER BY name) INTO unknown FROM roles WHERE sort_order IS NULL;
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'V012: ruoli senza sort_order previsto: %', unknown;
    END IF;
END $$;

ALTER TABLE roles ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE roles ADD CONSTRAINT roles_sort_order_key UNIQUE (sort_order);

-- ------------------------------------------------------------------------
-- Ricerca senza accenti e maiuscole
-- ------------------------------------------------------------------------

-- Estensioni "trusted" in PostgreSQL 16: le crea anche un utente non superuser con CREATE sul
-- database (abc_user in produzione, verificato il 28/09). IF NOT EXISTS: nessun errore se ci sono gia'.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- unaccent(text) e' STABLE (dipende da search_path): la forma a due argomenti con dizionario
-- esplicito, dentro una funzione SQL, si puo' dichiarare IMMUTABLE e quindi usare negli indici.
-- Nomi tutti qualificati con public: non dipende dal search_path di chi chiama.
CREATE OR REPLACE FUNCTION public.abc_fold(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT lower(public.unaccent('public.unaccent'::regdictionary, coalesce($1, '')))
$$;

-- Ricerca per nome e cognome (LIKE '%...%' sulla stessa espressione).
CREATE INDEX idx_users_name_fold_trgm ON users
    USING gin (public.abc_fold(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) public.gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Ricerca per email.
CREATE INDEX idx_users_email_fold_trgm ON users
    USING gin (public.abc_fold(email) public.gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Nessun indice nuovo per ruolo: idx_users_role (V001) copre il filtro e l'ordine per
-- roles.sort_order si fa su 8 righe (EXPLAIN nel referto del 28/09).
