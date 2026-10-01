#!/usr/bin/env bash
# Registro delle cancellazioni: serve a mantenere la promessa fatta agli utenti
# ("se si ripristina un backup, le cancellazioni chieste nel frattempo si rifanno subito").
#
# Come funziona:
#  - Quando un utente cancella il suo account, il sito NON toglie la riga: la tiene "anonimizzata"
#    (deleted_at valorizzato, email deleted+ID@deleted.invalid, nome e password vuoti).
#  - Questo script copia in un file, FUORI dal database, solo gli ID numerici di quegli utenti.
#    Nel file non c'e' nessun dato personale: sono solo numeri.
#  - Dopo ogni ripristino di un backup piu' vecchio, "riapplica" rifa' l'anonimizzazione di quegli ID
#    e fa in modo che quei numeri non vengano mai riassegnati a un nuovo utente.
#
# Uso:
#   registro-cancellazioni.sh aggiorna    aggiunge al registro gli ID degli utenti cancellati (di notte lo fa il backup)
#   registro-cancellazioni.sh riapplica   dopo un ripristino: rifa' le cancellazioni del registro
#   registro-cancellazioni.sh stato       quanti ID ci sono nel registro
#
# Gira in due posti: sul server (usa "docker compose exec postgres") e dentro il container "backup"
# (usa psql diretto, con le variabili PG* gia' impostate). In tutti e due i casi il file e' lo stesso.
set -Eeuo pipefail

MODO="${1:-}"
FILE_REGISTRO=""

# --- dove sta il registro e come si parla col database -----------------------
if [ -n "${REGISTRO_DIR:-}" ]; then
  # dentro il container backup (o una prova): REGISTRO_DIR e psql diretto
  DIR_REGISTRO="$REGISTRO_DIR"
  sql() { psql -v ON_ERROR_STOP=1 -At "$@"; }
else
  # shellcheck source=lib.sh
  . "$(dirname "$0")/lib.sh"
  DIR_REGISTRO="$(dati_dir)/registro-cancellazioni"
  sql() {
    dc exec -T postgres psql -v ON_ERROR_STOP=1 -At -U "$(env_sito DB_USER)" -d "$(env_sito DB_NAME)" "$@"
  }
fi
FILE_REGISTRO="$DIR_REGISTRO/utenti-cancellati.txt"
mkdir -p "$DIR_REGISTRO"
touch "$FILE_REGISTRO"

solo_numeri() { grep -E '^[0-9]+$' || true; }

aggiorna() {
  local nuovo
  nuovo="$(mktemp "$DIR_REGISTRO/.nuovo.XXXXXX")"
  { cat "$FILE_REGISTRO"; sql -c "SELECT id FROM users WHERE deleted_at IS NOT NULL ORDER BY id;"; } \
    | solo_numeri | sort -un > "$nuovo"
  mv "$nuovo" "$FILE_REGISTRO"
  chmod 640 "$FILE_REGISTRO"
  echo "registro cancellazioni aggiornato: $(wc -l < "$FILE_REGISTRO" | tr -d ' ') utenti"
}

riapplica() {
  local ids massimo
  ids="$(solo_numeri < "$FILE_REGISTRO" | paste -sd, -)"
  if [ -z "$ids" ]; then
    echo "registro cancellazioni vuoto: niente da riapplicare"
    return 0
  fi
  massimo="$(solo_numeri < "$FILE_REGISTRO" | sort -n | tail -n 1)"
  # Stessa anonimizzazione di AccountService.deleteAccount (piu' immagine del profilo),
  # sulle sole righe che esistono. Poi si toglie ogni token e si impedisce che questi ID
  # vengano riassegnati a utenti nuovi.
  sql <<SQL
BEGIN;
UPDATE users
   SET deleted_at = COALESCE(deleted_at, now()),
       active = false,
       email = 'deleted+' || id || '@deleted.invalid',
       password = NULL,
       first_name = NULL,
       last_name = NULL,
       oauth_provider = NULL,
       oauth_id = NULL,
       profile_picture_url = NULL
 WHERE id IN ($ids);
DELETE FROM tokens WHERE user_id IN ($ids);
SELECT setval('users_id_seq', GREATEST((SELECT last_value FROM users_id_seq), $massimo));
COMMIT;
SQL
  echo "cancellazioni riapplicate: $(echo "$ids" | tr ',' '\n' | wc -l | tr -d ' ') ID del registro controllati"
}

case "$MODO" in
  aggiorna)  aggiorna ;;
  riapplica) riapplica ;;
  stato)     echo "registro cancellazioni: $(solo_numeri < "$FILE_REGISTRO" | wc -l | tr -d ' ') utenti ($FILE_REGISTRO)" ;;
  *) echo "uso: $0 aggiorna | riapplica | stato" >&2; exit 2 ;;
esac
