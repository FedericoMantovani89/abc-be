# deploy/caddy — pagina "sito non disponibile" e manutenzione

- `pagine/sito-non-disponibile.html`: pagina autonoma (logo, stile, font e testo dentro un solo file).
- `abc-sito.caddy`: snippet `abc_sito` da importare nel Caddyfile (istruzioni in testa al file).
  Mostra la pagina per 502/503/504 e quando esiste `pagine/MANUTENZIONE`.
- `manutenzione.sh on|off|stato`: interruttore di manutenzione (crea/toglie `pagine/MANUTENZIONE`, senza riavviare Caddy).
- `Caddyfile.prova`: configurazione per provare lo snippet in locale (porta 8088, senza HTTPS).

Non e' ancora collegato al compose ne' al Caddyfile di produzione: lo fa il blocco C.
Provato con `caddy:2-alpine` in un container temporaneo: frontend acceso 200, frontend fermo 502 + pagina,
manutenzione accesa 503 + pagina (stessa pagina, `Cache-Control: no-store`, `Retry-After: 60`).
