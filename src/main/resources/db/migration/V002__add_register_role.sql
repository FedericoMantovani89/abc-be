-- ========================================================================
-- Ruolo REGISTER — utente appena registrato, in attesa di approvazione.
-- Gerarchia: PUBLIC < REGISTER < MEMBER < TECHNICIAN < DIRECTOR < STAFF < ADMIN/GOD
-- Un REGISTER NON accede alle aree riservate ai soci (MEMBER+); un admin
-- lo promuove a MEMBER per sbloccare l'area riservata.
-- ========================================================================

INSERT INTO roles (name, description) VALUES
    ('REGISTER', 'Registrato — in attesa di approvazione da un amministratore')
ON CONFLICT (name) DO NOTHING;
