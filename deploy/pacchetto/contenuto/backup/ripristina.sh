#!/usr/bin/env bash
# Scarica dalla Storage Box l'ULTIMO backup in una cartella (non tocca il sito):
#   /backup/ripristina.sh /destinazione  ->  /destinazione/db/abc.dump  e  /destinazione/file/dati/...
# Lo usano prova-ripristino.sh e ripristina.sh (sul server, vedi INSTALLA.md).
set -Eeuo pipefail
. "$(dirname "$0")/comune.sh"

DEST="${1:?uso: ripristina.sh CARTELLA_DI_DESTINAZIONE}"
controlla_configurazione || exit 1
mkdir -p "$DEST/db" "$DEST/file"

echo "backup disponibili:"
restic_sb snapshots --host abc-sito --compact

restic_sb restore latest --host abc-sito --tag db   --target "$DEST/db"
restic_sb restore latest --host abc-sito --tag file --target "$DEST/file"

[ -s "$DEST/db/abc.dump" ] || { echo "ERRORE: nel backup non c'e' il database (abc.dump)."; exit 1; }
echo "scaricato in $DEST: database $(du -h "$DEST/db/abc.dump" | cut -f1)"
