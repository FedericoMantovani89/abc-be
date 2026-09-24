-- ========================================================================
-- Fattori di zoom della locandina nella hero (hero zoom), per shows ed events.
-- NULL = 100 (comportamento attuale, nessuna modifica ai dati esistenti).
-- Valorizzato = percentuale rispetto al cover odierno (10..300) del fattore
-- di zoom da applicare alla locandina in hero. Desktop e mobile sono
-- indipendenti fra loro: ciascuna colonna e' o NULL o un valore nel range,
-- senza vincolo di coppia (a differenza del punto focale, V005).
--
-- INTEGER e non SMALLINT: le entity JPA (Show/Event, branch feat/hero-zoom-be)
-- mappano il campo come `Integer` senza columnDefinition, quindi Hibernate con
-- ddl-auto=validate si aspetta il tipo SQL "integer" — SMALLINT fallirebbe la
-- validazione dello schema all'avvio ("wrong column type ... found int2 ...
-- expecting integer"). Il range resta comunque vincolato dai CHECK sotto.
-- ========================================================================

ALTER TABLE shows
    ADD COLUMN hero_zoom_desktop INTEGER NULL,
    ADD COLUMN hero_zoom_mobile INTEGER NULL,
    ADD CONSTRAINT shows_hero_zoom_desktop_range CHECK (hero_zoom_desktop IS NULL OR hero_zoom_desktop BETWEEN 10 AND 300),
    ADD CONSTRAINT shows_hero_zoom_mobile_range CHECK (hero_zoom_mobile IS NULL OR hero_zoom_mobile BETWEEN 10 AND 300);

ALTER TABLE events
    ADD COLUMN hero_zoom_desktop INTEGER NULL,
    ADD COLUMN hero_zoom_mobile INTEGER NULL,
    ADD CONSTRAINT events_hero_zoom_desktop_range CHECK (hero_zoom_desktop IS NULL OR hero_zoom_desktop BETWEEN 10 AND 300),
    ADD CONSTRAINT events_hero_zoom_mobile_range CHECK (hero_zoom_mobile IS NULL OR hero_zoom_mobile BETWEEN 10 AND 300);
