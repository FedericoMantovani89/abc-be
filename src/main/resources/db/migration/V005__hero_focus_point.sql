-- ========================================================================
-- Punto focale della locandina (hero focus point), per shows ed events.
-- NULL = centro (comportamento attuale, nessuna modifica ai dati esistenti).
-- Valorizzato = percentuale 0..100 sull'asse x/y del punto da mantenere a
-- fuoco nei crop dell'immagine hero. Le due colonne vanno sempre valorizzate
-- insieme: o entrambe NULL o entrambe con un valore.
--
-- INTEGER e non SMALLINT: le entity JPA (Show/Event, branch feat/hero-focus-be)
-- mappano il campo come `Integer` senza columnDefinition, quindi Hibernate con
-- ddl-auto=validate si aspetta il tipo SQL "integer" — SMALLINT fallirebbe la
-- validazione dello schema all'avvio ("wrong column type ... found int2 ...
-- expecting integer"). Il range resta comunque vincolato dai CHECK sotto.
-- ========================================================================

ALTER TABLE shows
    ADD COLUMN hero_focus_x INTEGER NULL,
    ADD COLUMN hero_focus_y INTEGER NULL,
    ADD CONSTRAINT shows_hero_focus_x_range CHECK (hero_focus_x IS NULL OR hero_focus_x BETWEEN 0 AND 100),
    ADD CONSTRAINT shows_hero_focus_y_range CHECK (hero_focus_y IS NULL OR hero_focus_y BETWEEN 0 AND 100),
    ADD CONSTRAINT shows_hero_focus_both_or_none CHECK ((hero_focus_x IS NULL) = (hero_focus_y IS NULL));

ALTER TABLE events
    ADD COLUMN hero_focus_x INTEGER NULL,
    ADD COLUMN hero_focus_y INTEGER NULL,
    ADD CONSTRAINT events_hero_focus_x_range CHECK (hero_focus_x IS NULL OR hero_focus_x BETWEEN 0 AND 100),
    ADD CONSTRAINT events_hero_focus_y_range CHECK (hero_focus_y IS NULL OR hero_focus_y BETWEEN 0 AND 100),
    ADD CONSTRAINT events_hero_focus_both_or_none CHECK ((hero_focus_x IS NULL) = (hero_focus_y IS NULL));
