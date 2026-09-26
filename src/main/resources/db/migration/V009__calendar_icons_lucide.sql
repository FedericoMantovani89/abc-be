-- Le icone dei tipi di evento passano da Font Awesome (mai installato nel frontend, il valore
-- veniva solo stampato come testo) ai nomi lucide-react in kebab-case, scelti da un elenco chiuso
-- nel frontend (abc-fe lib/calendarIcons.tsx). La colonna resta una stringa libera: il backend
-- non valida l'elenco. Si converte per valore, non per id: tocca solo i valori di fabbrica.
UPDATE calendar_event_types SET icon_class = 'music'         WHERE icon_class = 'fa-music';
UPDATE calendar_event_types SET icon_class = 'drama'         WHERE icon_class = 'fa-masks-theater';
UPDATE calendar_event_types SET icon_class = 'users'         WHERE icon_class = 'fa-users';
UPDATE calendar_event_types SET icon_class = 'star'          WHERE icon_class = 'fa-star';
UPDATE calendar_event_types SET icon_class = 'calendar-days' WHERE icon_class = 'fa-calendar-day';

-- "Prova Ballerini" (creato a mano, senza icona): i passi di danza.
UPDATE calendar_event_types SET icon_class = 'footprints'
 WHERE name = 'Prova Ballerini' AND (icon_class IS NULL OR icon_class = '');
