-- Le prove si collegano allo spettacolo e ai ruoli del cast (calendar_event_rehearsal_roles):
-- la funzione "scene" viene rimossa. Ordine rispetto alle FK: prima le tabelle che puntano a
-- show_scenes, poi show_scenes. Nessun CASCADE: se qualcosa di inatteso le referenzia, fallisce.
DROP TABLE calendar_event_scenes;
DROP TABLE scene_cast_roles;
DROP TABLE show_scenes;
