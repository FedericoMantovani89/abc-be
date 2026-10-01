#!/usr/bin/env bash
# Scarica dalla Storage Box l'ULTIMO backup in una cartella (non tocca il sito):
#   /backup/ripristina.sh /destinazione           ->  /destinazione/db/abc.dump  e  /destinazione/file/dati/...
#   /backup/ripristina.sh /destinazione --prova   ->  come sopra ma SENZA scaricare i file caricati (solo li conta)
# Lo usano prova-ripristino.sh e ripristina.sh (sul server, vedi INSTALLA.md).
set -Eeuo pipefail
. "$(dirname "$0")/comune.sh"

DEST="${1:?uso: ripristina.sh CARTELLA_DI_DESTINAZIONE [--prova]}"
PROVA=0
[ "${2:-}" = "--prova" ] && PROVA=1
controlla_configurazione || exit 1
mkdir -p "$DEST/db" "$DEST/file"

echo "backup disponibili:"
restic_sb snapshots --host abc-sito --compact

restic_sb restore latest --host abc-sito --tag db --target "$DEST/db"
if [ "$PROVA" = "1" ]; then
  # file caricati: non si scaricano (possono pesare decine di GB), ma si controlla che ci siano
  restic_sb restore latest --host abc-sito --tag file --target "$DEST/file" --exclude /dati/uploads
  n=$(restic_sb ls latest --host abc-sito --tag file /dati/uploads | wc -l)
  echo "file caricati presenti nel backup (voci): $n" | tee "$DEST/conteggio-file-caricati.txt"
else
  restic_sb restore latest --host abc-sito --tag file --target "$DEST/file"
fi

[ -s "$DEST/db/abc.dump" ] || { echo "ERRORE: nel backup non c'e' il database (abc.dump)."; exit 1; }
echo "scaricato in $DEST: database $(du -h "$DEST/db/abc.dump" | cut -f1)"
