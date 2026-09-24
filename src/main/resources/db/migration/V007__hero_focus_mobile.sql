-- ========================================================================
-- Secondo punto focale della locandina (hero focus point), per il mobile,
-- su shows ed events. NULL = centro (comportamento attuale, nessuna
-- modifica ai dati esistenti). Valorizzato = percentuale 0..100 sull'asse
-- x/y del punto da mantenere a fuoco nei crop dell'immagine hero su mobile.
-- Le due colonne vanno sempre valorizzate insieme: o entrambe NULL o
-- entrambe con un valore. Indipendente dal punto focale desktop (V005):
-- nessun vincolo incrociato fra i due punti.
--
-- INTEGER e non SMALLINT: le entity JPA (Show/Event, branch
-- feat/hero-focus-mobile-be) mappano il campo come `Integer` senza
-- columnDefinition, quindi Hibernate con ddl-auto=validate si aspetta il
-- tipo SQL "integer" — SMALLINT fallirebbe la validazione dello schema
-- all'avvio ("wrong column type ... found int2 ... expecting integer").
-- Il range resta comunque vincolato dai CHECK sotto (stesso motivo di V005
-- e V006).
--
-- Decisione di Federico: il punto focale gia' impostato per il desktop
-- vale anche per il mobile finche' non viene impostato esplicitamente un
-- punto mobile diverso. Percio' l'UPDATE sotto copia hero_focus_x/y nelle
-- nuove colonne mobile per le righe dove il punto desktop e' gia'
-- valorizzato (righe con punto desktop NULL restano NULL anche su mobile).
-- ========================================================================

ALTER TABLE shows
    ADD COLUMN hero_focus_mobile_x INTEGER NULL,
    ADD COLUMN hero_focus_mobile_y INTEGER NULL,
    ADD CONSTRAINT shows_hero_focus_mobile_x_range CHECK (hero_focus_mobile_x IS NULL OR hero_focus_mobile_x BETWEEN 0 AND 100),
    ADD CONSTRAINT shows_hero_focus_mobile_y_range CHECK (hero_focus_mobile_y IS NULL OR hero_focus_mobile_y BETWEEN 0 AND 100),
    ADD CONSTRAINT shows_hero_focus_mobile_both_or_none CHECK ((hero_focus_mobile_x IS NULL) = (hero_focus_mobile_y IS NULL));

ALTER TABLE events
    ADD COLUMN hero_focus_mobile_x INTEGER NULL,
    ADD COLUMN hero_focus_mobile_y INTEGER NULL,
    ADD CONSTRAINT events_hero_focus_mobile_x_range CHECK (hero_focus_mobile_x IS NULL OR hero_focus_mobile_x BETWEEN 0 AND 100),
    ADD CONSTRAINT events_hero_focus_mobile_y_range CHECK (hero_focus_mobile_y IS NULL OR hero_focus_mobile_y BETWEEN 0 AND 100),
    ADD CONSTRAINT events_hero_focus_mobile_both_or_none CHECK ((hero_focus_mobile_x IS NULL) = (hero_focus_mobile_y IS NULL));

UPDATE shows
SET hero_focus_mobile_x = hero_focus_x,
    hero_focus_mobile_y = hero_focus_y
WHERE hero_focus_x IS NOT NULL;

UPDATE events
SET hero_focus_mobile_x = hero_focus_x,
    hero_focus_mobile_y = hero_focus_y
WHERE hero_focus_x IS NOT NULL;
