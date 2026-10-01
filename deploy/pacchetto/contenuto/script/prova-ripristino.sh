#!/usr/bin/env bash
# prova-ripristino.sh — PROVA che i backup si aprano davvero. Non tocca il sito.
#
# Scarica l'ultimo backup dalla Storage Box, lo ricarica in un database USA E GETTA (un container
# temporaneo che sparisce alla fine), rifa' le cancellazioni del registro e stampa un riepilogo.
# I file caricati non vengono scaricati (possono pesare decine di GB): si controlla che nel backup ci siano.
#
# Si fa su un server usa e getta (o anche sul server vero, e' sicuro), da ripetere ogni 3 mesi:
#   1. sul server: cartella del pacchetto, prima parte di installa.sh (crea /opt/abc-sito/.env)
#   2. nel file .env scrivi solo BACKUP_REPOSITORY, BACKUP_PASSWORD (e BACKUP_SSH_PORTA); la chiave SSH
#      della Storage Box va in /opt/abc-sito/segreti/backup_key
#   3. bash script/prova-ripristino.sh
#
# Se la prova FALLISCE, i backup non sono affidabili: non ignorarla, scrivi a Federico.
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

passo "1/6 Controlli"
serve_comando docker
docker info >/dev/null 2>&1 || fermati "Docker non risponde. Sei l'utente deploy?"
ENV_FILE="$CARTELLA_SITO/.env"
[ -f "$ENV_FILE" ] || fermati "Manca $ENV_FILE (vedi le istruzioni qui sopra, punto 1)."
REPO="$(leggi_env "$ENV_FILE" BACKUP_REPOSITORY)"
PASSWORD="$(leggi_env "$ENV_FILE" BACKUP_PASSWORD)"
[ -n "$REPO" ] && [ -n "$PASSWORD" ] || fermati "Nel file .env mancano BACKUP_REPOSITORY e/o BACKUP_PASSWORD."
[ -f "$CARTELLA_SITO/segreti/backup_key" ] || fermati "Manca la chiave SSH della Storage Box: $CARTELLA_SITO/segreti/backup_key"
VERSIONE="$(versione_installata)"; VERSIONE="${VERSIONE:-$(versione_pacchetto)}"
IMG_BACKUP="abc-musical/backup:$VERSIONE"
if ! docker image inspect "$IMG_BACKUP" >/dev/null 2>&1; then
  info "carico le immagini dal pacchetto ..."
  carica_immagini
fi
docker image inspect "postgres:16-alpine" >/dev/null 2>&1 || fermati "Manca l'immagine postgres:16-alpine: carica le immagini del pacchetto (docker load)."

TMP="$(mktemp -d /var/tmp/abc-prova-ripristino.XXXXXX)"
RETE="abc-prova-ripristino"
PG="abc-prova-ripristino-pg"
ENVTMP="$(mktemp)"
chmod 600 "$ENVTMP"
pulisci() {
  docker rm -f "$PG" >/dev/null 2>&1 || true
  docker network rm "$RETE" >/dev/null 2>&1 || true
  rm -rf "$TMP" "$ENVTMP"
}
trap 'pulisci' EXIT
trap '_se_errore $LINENO' ERR
printf 'RESTIC_REPOSITORY=%s\nRESTIC_PASSWORD=%s\nBACKUP_SSH_PORTA=%s\n' "$REPO" "$PASSWORD" "$(leggi_env "$ENV_FILE" BACKUP_SSH_PORTA)" > "$ENVTMP"

passo "2/6 Scarico l'ultimo backup (senza i file caricati)"
docker run --rm --env-file "$ENVTMP" -v "$CARTELLA_SITO/segreti:/segreti" -v "$TMP:/ripristino" \
  --entrypoint /backup/ripristina.sh "$IMG_BACKUP" /ripristino --prova
DUMP="$TMP/db/abc.dump"
REG_FILE="$TMP/file/dati/registro-cancellazioni/utenti-cancellati.txt"

passo "3/6 Avvio un database usa e getta"
docker network create "$RETE" >/dev/null
docker run -d --rm --name "$PG" --network "$RETE" -e POSTGRES_USER=abc -e POSTGRES_PASSWORD=prova -e POSTGRES_DB=abc postgres:16-alpine >/dev/null
for i in $(seq 1 30); do
  docker exec "$PG" pg_isready -q -U abc -d abc 2>/dev/null && break
  sleep 2
  [ "$i" -lt 30 ] || fermati "Il database temporaneo non parte."
done
ok "database temporaneo pronto"

passo "4/6 Ricarico il backup nel database temporaneo"
docker exec -i "$PG" pg_restore -U abc -d abc --no-owner --exit-on-error < "$DUMP"
ok "pg_restore finito senza errori"

passo "5/6 Rifaccio le cancellazioni del registro"
mkdir -p "$TMP/registro"
[ -f "$REG_FILE" ] && cp "$REG_FILE" "$TMP/registro/utenti-cancellati.txt"
docker run --rm --network "$RETE" -e REGISTRO_DIR=/r -e PGHOST="$PG" -e PGUSER=abc -e PGPASSWORD=prova -e PGDATABASE=abc \
  -v "$TMP/registro:/r" --entrypoint /backup/registro-cancellazioni.sh "$IMG_BACKUP" riapplica

passo "6/6 Controlli sul contenuto"
q() { docker exec "$PG" psql -At -U abc -d abc -c "$1"; }
TABELLE="$(q "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")"
UTENTI="$(q "SELECT count(*) FROM users WHERE deleted_at IS NULL;")"
CANCELLATI="$(q "SELECT count(*) FROM users WHERE deleted_at IS NOT NULL;")"
MIGRAZIONE="$(q "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")"
NON_ANONIMI="$(q "SELECT count(*) FROM users WHERE deleted_at IS NOT NULL AND (email NOT LIKE 'deleted+%' OR password IS NOT NULL OR first_name IS NOT NULL);")"
[ "$TABELLE" -ge 10 ] || fermati "Nel backup ci sono solo $TABELLE tabelle: il backup e' incompleto."
[ -n "$MIGRAZIONE" ] || fermati "Nel backup manca la storia delle migrazioni (flyway_schema_history)."
[ "$NON_ANONIMI" = "0" ] || fermati "$NON_ANONIMI utenti cancellati non risultano anonimizzati dopo il ripristino."

cat <<FINE

== PROVA DI RIPRISTINO RIUSCITA ($(date '+%F %T'))
   tabelle nel database ripristinato : $TABELLE
   ultima migrazione                  : $MIGRAZIONE
   utenti attivi / cancellati         : $UTENTI / $CANCELLATI
   $(cat "$TMP/conteggio-file-caricati.txt" 2>/dev/null || echo "file caricati: conteggio non disponibile")
   Scrivi la data di oggi e "prova ripristino riuscita" nel registro delle prove di ABC (prossima: tra 3 mesi).
   Il database temporaneo viene cancellato adesso.
FINE
