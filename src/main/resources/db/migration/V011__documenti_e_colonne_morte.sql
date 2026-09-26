-- ========================================================================
-- Archivio documenti: un solo modello di permessi (i ruoli della cartella, in una tabella
-- dedicata) e via le colonne morte. Referto audit-database-2026-09-26, problemi 1, 2, 3, 4,
-- 8, 11, 12; decisioni di Federico del 26/09 ("Documenti e database").
-- Tabelle folders e documents vuote in produzione al 26/09: nessun dato perso.
-- ========================================================================

-- folder_roles.role_name punta a roles(name): la chiave esterna richiede un vincolo unico su
-- roles.name, che c'e' gia' da V001 (roles_name_key). Nessuna modifica a roles.

-- Problemi 1, 3, 8: i ruoli della cartella passano da testo con virgole a una tabella legata a roles.
CREATE TABLE folder_roles (
    folder_id BIGINT      NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    role_name VARCHAR(50) NOT NULL REFERENCES roles(name),
    PRIMARY KEY (folder_id, role_name)
);

-- Problema 8: copia dei ruoli esistenti (0 cartelle oggi). Un ruolo che non esiste in roles fa
-- fallire la migrazione per la chiave esterna: meglio fermarsi che perdere un permesso in silenzio.
INSERT INTO folder_roles (folder_id, role_name)
SELECT DISTINCT f.id, trim(r.role_name)
  FROM folders f
 CROSS JOIN LATERAL unnest(string_to_array(f.allowed_roles, ',')) AS r(role_name)
 WHERE f.allowed_roles IS NOT NULL
   AND trim(r.role_name) <> '';

-- Problemi 1, 3, 8: la vecchia colonna di testo non serve piu'.
ALTER TABLE folders DROP COLUMN allowed_roles;

-- Problemi 2, 3, 11: via il secondo sistema di permessi (visibility) e le colonne mai usate.
-- updated_by si porta via anche la sua chiave esterna verso users.
ALTER TABLE documents
    DROP COLUMN visibility,
    DROP COLUMN description,
    DROP COLUMN document_category,
    DROP COLUMN updated_by;

-- Problema 4: cancellare una cartella cancella i suoi documenti, mai piu' in radice senza permessi.
ALTER TABLE documents
    DROP CONSTRAINT documents_folder_id_fkey,
    ADD CONSTRAINT documents_folder_id_fkey
        FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE CASCADE;

-- Problemi 11, 12: nessuna logica di ricorrenza e nessun legame con events (0 righe valorizzate).
ALTER TABLE calendar_events
    DROP COLUMN is_recurring,
    DROP COLUMN recurrence_pattern,
    DROP COLUMN public_event_id;

-- Problema 11: dato personale scritto dal login OAuth e mai letto.
ALTER TABLE users DROP COLUMN profile_picture_url;

-- Indici: nessuno era sulle colonne tolte (V001..V009 verificati), nessun indice da togliere.
