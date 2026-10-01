#!/usr/bin/env bash
# Un giro di backup: registro cancellazioni, database, file caricati. Tutto cifrato con restic.
# Conserva 7 copie giornaliere, 4 settimanali, 12 mensili. Si lancia da solo ogni notte (ciclo.sh)
# e a mano con:  docker compose exec backup /backup/backup.sh
set -Eeuo pipefail
. "$(dirname "$0")/comune.sh"

echo "== $(date '+%F %T') inizio backup"

# 1. Il registro delle cancellazioni si aggiorna SEMPRE, anche se la Storage Box non e' configurata.
REGISTRO_DIR=/dati/registro-cancellazioni /backup/registro-cancellazioni.sh aggiorna

if ! controlla_configurazione; then
  echo "== backup NON eseguito (configurazione mancante)"
  exit 1
fi

# 2. Prima volta: crea l'archivio cifrato.
if ! restic_sb cat config >/dev/null 2>&1; then
  echo "archivio dei backup non trovato: lo creo"
  restic_sb init
fi

# 3. Database (formato compresso di pg_dump, senza file temporanei sul disco)
pg_dump -Fc --no-owner | restic_sb backup --stdin --stdin-filename abc.dump --tag db --host abc-sito

# 4. File caricati, configurazione, registro cancellazioni
restic_sb backup --tag file --host abc-sito /dati/uploads /dati/config /dati/registro-cancellazioni

# 5. Pulizia: 7 giornaliere, 4 settimanali, 12 mensili
restic_sb forget --host abc-sito --keep-daily 7 --keep-weekly 4 --keep-monthly 12 --prune

# 6. Di domenica si controlla anche che l'archivio sia integro (legge un campione dei dati)
if [ "$(date +%u)" = "7" ]; then
  restic_sb check --read-data-subset=5%
fi

echo "== $(date '+%F %T') BACKUP OK"
