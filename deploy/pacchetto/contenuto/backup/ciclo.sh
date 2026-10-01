#!/usr/bin/env bash
# Entrypoint del container "backup": aspetta l'ora del backup (BACKUP_ORA, ora di Roma) e lo esegue,
# ogni notte. "ciclo.sh subito" fa un solo giro e termina.
set -uo pipefail

if [ "${1:-}" = "subito" ]; then
  exec /backup/backup.sh
fi

ORA="${BACKUP_ORA:-03:30}"
echo "servizio backup avviato: un giro ogni notte alle ${ORA} (ora di Roma)"

while true; do
  adesso=$(date +%s)
  bersaglio=$(date -d "$(date +%F) ${ORA}:00" +%s 2>/dev/null || echo "")
  if [ -z "$bersaglio" ]; then
    echo "ERRORE: BACKUP_ORA non valida (\"$ORA\"): scrivi per esempio 03:30. Riprovo tra un'ora."
    sleep 3600
    continue
  fi
  if [ "$bersaglio" -le "$adesso" ]; then
    bersaglio=$((bersaglio + 86400))
  fi
  attesa=$((bersaglio - adesso))
  echo "prossimo backup tra ${attesa} secondi"
  sleep "$attesa"
  /backup/backup.sh || echo "ERRORE: il backup di stanotte e' FALLITO. Controlla i messaggi qui sopra e il file .env."
  sleep 60
done
