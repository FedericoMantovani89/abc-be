#!/usr/bin/env bash
# ripristina.sh — RIPRISTINO VERO dall'ultimo backup della Storage Box (emergenza).
#
# Quando serve: il server e' andato perso o i dati sono rovinati.
# Su un server nuovo: prima prepara-server.sh e installa.sh (con lo STESSO file .env di prima e la
# chiave SSH della Storage Box in segreti/), poi questo script. Dettagli in INSTALLA.md, "Se il server si rompe".
#
#   bash script/ripristina.sh
#
# ATTENZIONE: SOSTITUISCE database, file caricati e configurazione di questo server con quelli
# dell'ultimo backup (di solito quello di stanotte). I dati attuali vengono messi da parte, non cancellati
# (cartelle "*.prima-del-ripristino-DATA" nel Volume: vanno cancellate a mano quando non servono piu').
# Dopo il ripristino le cancellazioni di account chieste dopo quel backup vengono RIFATTE.
#
# Per PROVARE il ripristino senza toccare il sito usa invece prova-ripristino.sh.
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

passo "1/8 Controlli"
serve_comando docker
[ -e "$CARTELLA_SITO/.installato" ] || fermati "Il sito non e' installato in $CARTELLA_SITO: esegui prima installa.sh."
[ -n "$(env_sito BACKUP_REPOSITORY)" ] && [ -n "$(env_sito BACKUP_PASSWORD)" ] \
  || fermati "BACKUP_REPOSITORY e BACKUP_PASSWORD non sono nel file .env: senza non si apre nessun backup."
[ -f "$CARTELLA_SITO/segreti/backup_key" ] || fermati "Manca la chiave SSH della Storage Box: $CARTELLA_SITO/segreti/backup_key"
DATI="$(dati_dir)"
chiedi_parola "RIPRISTINA" "Questo SOSTITUISCE i dati di questo server con l'ultimo backup. Sei sicuro?"
ADESSO="$(date +%Y%m%d-%H%M)"
TMP="$DATI/ripristino-in-corso"

passo "2/8 Accendo la manutenzione e salvo il registro delle cancellazioni"
manutenzione on
# se il database attuale funziona, le cancellazioni recenti finiscono nel registro prima di sostituirlo
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" aggiorna || avviso "Non sono riuscito ad aggiornare il registro dal database attuale (se il database e' rotto e' normale)."

passo "3/8 Scarico l'ultimo backup (puo' servire parecchio tempo)"
rm -rf "$TMP"; mkdir -p "$TMP"
dc run --rm --no-deps --entrypoint /backup/ripristina.sh -v "$TMP:/ripristino" backup /ripristino

passo "4/8 Fermo il sito"
dc stop frontend backend backup || true

passo "5/8 Sostituisco file caricati e configurazione"
for cartella in uploads config; do
  if [ -d "$DATI/$cartella" ] && [ -d "$TMP/file/dati/$cartella" ]; then
    mv "$DATI/$cartella" "$DATI/$cartella.prima-del-ripristino-$ADESSO"
    mv "$TMP/file/dati/$cartella" "$DATI/$cartella"
    ok "$cartella ripristinata (la versione di prima e' in $cartella.prima-del-ripristino-$ADESSO)"
  fi
done
BE_UID="$(docker run --rm --entrypoint id "abc-musical/backend:$(versione_installata)" -u abc)"
BE_GID="$(docker run --rm --entrypoint id "abc-musical/backend:$(versione_installata)" -g abc)"
sudo chown -R "$BE_UID:$BE_GID" "$DATI/uploads"

passo "6/8 Registro delle cancellazioni"
# si uniscono il registro del backup e quello gia' presente su questo server
mkdir -p "$DATI/registro-cancellazioni"
RESTAURATO="$TMP/file/dati/registro-cancellazioni/utenti-cancellati.txt"
ATTUALE="$DATI/registro-cancellazioni/utenti-cancellati.txt"
touch "$ATTUALE"
if [ -f "$RESTAURATO" ]; then
  cat "$ATTUALE" "$RESTAURATO" | grep -E '^[0-9]+$' | sort -un > "$ATTUALE.nuovo"
  mv "$ATTUALE.nuovo" "$ATTUALE"
fi
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" stato

passo "7/8 Ripristino il database"
ripristina_database "$TMP/db/abc.dump"

passo "8/8 Riavvio il sito"
rm -rf "$TMP"
dc up -d
attendi_sito_sano
manutenzione off

cat <<FINE

== RIPRISTINO FINITO.
   Controlla il sito: accesso, un documento, una foto.
   Le cartelle "*.prima-del-ripristino-$ADESSO" in $DATI contengono i dati di prima:
   cancellale a mano quando hai verificato che va tutto bene.
   Se questo e' un server nuovo: ricorda di spostare il DNS (INSTALLA.md, passo 8).
FINE
