#!/usr/bin/env bash
# Funzioni comuni degli script del pacchetto ABC. Non si lancia da solo: lo caricano gli altri script.
# Regole di tutti gli script: messaggi in italiano, si fermano al PRIMO errore, non scrivono mai
# i segreti a schermo.

set -Eeuo pipefail

# Dove vive il sito installato (compose, .env, versione). Si puo' cambiare con CARTELLA_SITO.
CARTELLA_SITO="${CARTELLA_SITO:-/opt/abc-sito}"
# Cartella del pacchetto da cui gira lo script (quella dove si e' decompresso lo zip).
PACCHETTO="$(cd "$(dirname "${BASH_SOURCE[1]:-$0}")/.." && pwd)"
PASSO_CORRENTE="avvio"

if [ -t 1 ]; then
  ROSSO=$'\033[31m'; VERDE=$'\033[32m'; GIALLO=$'\033[33m'; GRASSETTO=$'\033[1m'; FINE=$'\033[0m'
else
  ROSSO=""; VERDE=""; GIALLO=""; GRASSETTO=""; FINE=""
fi

passo()  { PASSO_CORRENTE="$*"; printf '\n%s== %s%s\n' "$GRASSETTO" "$*" "$FINE"; }
ok()     { printf '%s   ok%s %s\n' "$VERDE" "$FINE" "$*"; }
info()   { printf '   %s\n' "$*"; }
avviso() { printf '%s   ATTENZIONE:%s %s\n' "$GIALLO" "$FINE" "$*" >&2; }
errore() { printf '%s   ERRORE:%s %s\n' "$ROSSO" "$FINE" "$*" >&2; }
fermati() { errore "$*"; exit 1; }

# Se un comando fallisce, lo script si ferma e dice a che punto era.
_se_errore() {
  local codice=$? riga=$1
  errore "Lo script si e' fermato al passo: \"${PASSO_CORRENTE}\" (riga ${riga}, codice ${codice})."
  errore "Leggi il messaggio sopra. Non rilanciare a caso: se non e' chiaro, scrivi a Federico copiando le ultime righe (senza password)."
  exit "$codice"
}
trap '_se_errore $LINENO' ERR

# chiedi_si "Domanda": risponde sempre si' se SI=1 (opzione --si).
chiedi_si() {
  if [ "${SI:-0}" = "1" ]; then return 0; fi
  local risposta
  printf '%s   %s [scrivi si e premi Invio]: %s' "$GRASSETTO" "$1" "$FINE"
  read -r risposta
  [ "$risposta" = "si" ] || [ "$risposta" = "sì" ] || fermati "Annullato: nessuna modifica fatta da qui in poi."
}

# chiedi_parola PAROLA "Domanda": serve per i comandi pericolosi, bisogna scrivere la parola intera.
chiedi_parola() {
  local parola=$1 risposta
  printf '%s   %s%s\n   Per continuare scrivi esattamente %s e premi Invio: ' "$GRASSETTO" "$2" "$FINE" "$parola"
  read -r risposta
  [ "$risposta" = "$parola" ] || fermati "Annullato: nessuna modifica fatta."
}

serve_comando() {
  command -v "$1" >/dev/null 2>&1 || fermati "Manca il programma \"$1\". ${2:-Esegui prima prepara-server.sh (vedi INSTALLA.md).}"
}

# leggi_env FILE CHIAVE -> stampa il valore (senza virgolette). NON si fa mai "source .env":
# i valori possono contenere spazi (per esempio il RUNTS).
leggi_env() {
  local file=$1 chiave=$2 valore
  [ -f "$file" ] || return 0
  valore=$(grep -E "^${chiave}=" "$file" | tail -n 1 | cut -d= -f2- | tr -d '\r' || true)
  valore="${valore%\"}"; valore="${valore#\"}"
  valore="${valore%\'}"; valore="${valore#\'}"
  printf '%s' "$valore"
}

env_sito() { leggi_env "$CARTELLA_SITO/.env" "$1"; }
dati_dir() { local d; d=$(env_sito DATI_DIR); printf '%s' "${d:-/mnt/abc-dati}"; }
versione_installata() { leggi_env "$CARTELLA_SITO/versione.env" VERSIONE; }
versione_pacchetto()  { leggi_env "$PACCHETTO/versione.env" VERSIONE; }

# dc <argomenti di docker compose>: il compose del sito installato, con .env e versione.env.
dc() {
  docker compose --project-directory "$CARTELLA_SITO" \
    --env-file "$CARTELLA_SITO/.env" --env-file "$CARTELLA_SITO/versione.env" \
    -f "$CARTELLA_SITO/docker-compose.prod.yml" "$@"
}

# Controlla che nel .env non ci siano segreti rimasti vuoti (solo quelli obbligatori).
controlla_env() {
  local file=$1 mancanti="" chiave
  [ -f "$file" ] || fermati "Non trovo il file $file. Copialo da .env.esempio e compilalo (INSTALLA.md)."
  for chiave in JWT_SECRET NEXTAUTH_SECRET REMEMBER_ME_KEY DB_PASSWORD ADMIN_EMAIL ADMIN_PASSWORD SMTP_USER SMTP_PASSWORD; do
    [ -n "$(leggi_env "$file" "$chiave")" ] || mancanti="$mancanti $chiave"
  done
  if [ -n "$mancanti" ]; then
    errore "Nel file .env mancano questi valori:${mancanti}"
    fermati "Compilali (i comandi per generarli sono scritti nel file .env.esempio) e rilancia lo script."
  fi
  case "$(leggi_env "$file" JWT_SECRET)" in
    ????????????????????????????????*) ;;
    *) fermati "JWT_SECRET e' troppo corto: usa il comando scritto in .env.esempio (almeno 32 caratteri)." ;;
  esac
}

# Controlla gli hash SHA256 dei file del pacchetto (SHA256SUMS.txt).
# Distingue "non riesco a controllare" (programma o file di controllo che non funziona) da "file diverso".
controlla_hash_pacchetto() {
  local uscita codice=0
  [ -f "$PACCHETTO/SHA256SUMS.txt" ] || fermati "Manca SHA256SUMS.txt: il pacchetto e' incompleto. Riscaricalo."
  command -v sha256sum >/dev/null 2>&1 || fermati "Non riesco a controllare il pacchetto: manca il programma sha256sum su questo server."
  uscita="$(cd "$PACCHETTO" && sha256sum -c SHA256SUMS.txt 2>&1)" || codice=$?
  if [ "$codice" -ne 0 ]; then
    if printf '%s
' "$uscita" | grep -q ': FAILED'; then
      printf '%s
' "$uscita" | grep -E 'FAILED|No such file' | head -n 5 >&2
      fermati "Un file del pacchetto e' diverso dall'originale (hash non corrispondente). Riscarica lo zip e controlla l'impronta SHA-256."
    fi
    printf '%s
' "$uscita" | head -n 5 >&2
    fermati "Non riesco a controllare il pacchetto (sha256sum ha dato un errore, ma non risulta nessun file diverso). Controlla SHA256SUMS.txt o riscarica lo zip."
  fi
  ok "tutti i file del pacchetto corrispondono agli hash"
}

# come_root COMANDO...: lo esegue come root (l'utente deploy ha sudo senza password da prepara-server.sh).
come_root() {
  if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo -n "$@"; fi
}

# rimuovi_cartella CARTELLA: la cancella anche se dentro ci sono file di root (restic, container backup).
# Non fa fallire lo script: se non ci riesce lo dice e dice come fare a mano.
rimuovi_cartella() {
  local c=$1
  [ -e "$c" ] || return 0
  rm -rf "$c" 2>/dev/null && return 0
  come_root rm -rf "$c" 2>/dev/null && return 0
  avviso "Non sono riuscito a cancellare $c. Cancellala tu:  sudo rm -rf $c"
  return 1
}

# sposta_cartella DA A: mv, con sudo se le cartelle appartengono a un altro utente (uploads: utente del backend).
sposta_cartella() {
  mv "$1" "$2" 2>/dev/null || come_root mv "$1" "$2"
}

carica_immagini() {
  local f
  for f in "$PACCHETTO"/immagini/*.tar.gz; do
    [ -f "$f" ] || fermati "Nel pacchetto non ci sono le immagini (cartella immagini/)."
    info "carico $(basename "$f") ..."
    gunzip -c "$f" | docker load >/dev/null
  done
  ok "immagini caricate"
}

# Aspetta che backend e frontend rispondano (massimo 4 minuti).
# Si usa 127.0.0.1 e NON localhost: nel container "localhost" puo' risolvere su ::1, dove Next non ascolta.
attendi_sito_sano() {
  local i stato
  info "aspetto che il sito si avvii (puo' servire 1-2 minuti) ..."
  for i in $(seq 1 48); do
    stato=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$(dc ps -q backend)" 2>/dev/null || echo "assente")
    if [ "$stato" = "healthy" ] && dc exec -T frontend wget -q -O /dev/null http://127.0.0.1:3000/ 2>/dev/null; then
      ok "backend e frontend rispondono"
      return 0
    fi
    sleep 5
  done
  errore "Il sito non e' partito entro 4 minuti (backend: ${stato:-?})."
  errore "Guarda i registri:  journalctl CONTAINER_TAG=abc-abc-sito-backend-1 -n 80   oppure  docker compose logs backend"
  return 1
}

# Il file del registro cancellazioni deve restare di deploy (lo riscrive anche il container backup,
# che gira come root). Se e' rimasto di root (versioni vecchie) lo si rimette a posto con sudo.
sistema_registro() {
  local d f; d="$(dati_dir)/registro-cancellazioni"; f="$d/utenti-cancellati.txt"
  mkdir -p "$d" 2>/dev/null || come_root mkdir -p "$d"
  if [ ! -w "$d" ] || { [ -e "$f" ] && { [ ! -r "$f" ] || [ ! -w "$f" ]; }; }; then
    come_root chown -R "$(id -u):$(id -g)" "$d"
    come_root chmod -R u+rwX,go+rX "$d"
  fi
}

# Una riga sola per riaccendere/spegnere: serve nei messaggi d'errore.
RIGA_MANUTENZIONE_OFF="sh $CARTELLA_SITO/caddy/manutenzione.sh off"

manutenzione() { sh "$CARTELLA_SITO/caddy/manutenzione.sh" "$1"; }

# Aspetta che Postgres (quello del sito) accetti connessioni.
attendi_postgres() {
  local i
  for i in $(seq 1 30); do
    if dc exec -T postgres pg_isready -q -U "$(env_sito DB_USER)" -d postgres 2>/dev/null; then return 0; fi
    sleep 2
  done
  fermati "Postgres non risponde."
}

# ripristina_database FILE.dump : SOSTITUISCE il database del sito con quello del file.
# Ferma frontend, backend e backup, ricrea il database vuoto, ricarica il file e riapplica
# il registro delle cancellazioni. Non riavvia i servizi: lo fa chi chiama.
ripristina_database() {
  local file=$1 utente nome
  [ -s "$file" ] || fermati "Il file del database non esiste o e' vuoto: $file"
  utente="$(env_sito DB_USER)"; nome="$(env_sito DB_NAME)"
  info "fermo frontend, backend e backup ..."
  dc stop frontend backend backup
  dc up -d postgres
  attendi_postgres
  info "ricreo il database $nome ..."
  dc exec -T postgres psql -v ON_ERROR_STOP=1 -U "$utente" -d postgres \
    -c "DROP DATABASE IF EXISTS \"$nome\" WITH (FORCE);" -c "CREATE DATABASE \"$nome\" OWNER \"$utente\";"
  info "ricarico i dati dalla copia (puo' servire qualche minuto) ..."
  dc exec -T postgres pg_restore -U "$utente" -d "$nome" --no-owner --exit-on-error < "$file"
  ok "database ripristinato"
  info "riapplico le cancellazioni degli utenti ..."
  bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" riapplica
}
