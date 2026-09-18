-- ========================================================================
-- Flag "visualizza in home", accendibile/spegnibile dall'area admin.
-- Decide quali spettacoli compaiono nella sezione Repertorio della home
-- (in aggiunta al requisito gia' esistente della locandina, non al suo posto).
-- Gli spettacoli esistenti partono tutti spenti (FALSE): l'attivazione e'
-- una scelta manuale che va fatta uno per uno dall'admin.
-- ========================================================================

ALTER TABLE shows ADD COLUMN show_in_home BOOLEAN NOT NULL DEFAULT FALSE;
