#!/usr/bin/env bash
# Parte comune degli script di backup (dentro il container "backup"). Non si lancia da solo.
set -Eeuo pipefail

# Cartella con la chiave SSH della Storage Box (/segreti/backup_key) e l'elenco dei server fidati.
SEGRETI="${SEGRETI:-/segreti}"

controlla_configurazione() {
  if [ -z "${RESTIC_REPOSITORY:-}" ] || [ -z "${RESTIC_PASSWORD:-}" ]; then
    echo "ATTENZIONE: il backup sulla Storage Box NON e' configurato (BACKUP_REPOSITORY / BACKUP_PASSWORD vuoti nel file .env)."
    return 1
  fi
  [ -f "$SEGRETI/backup_key" ] || { echo "ERRORE: manca la chiave SSH $SEGRETI/backup_key (vedi INSTALLA.md, passo Backup)."; return 1; }
  chmod 600 "$SEGRETI/backup_key" 2>/dev/null || true
  return 0
}

# restic con SFTP verso la Storage Box (porta 23, accesso con chiave)
restic_sb() {
  local repo utente_host
  repo="${RESTIC_REPOSITORY#sftp:}"
  utente_host="${repo%%:*}"
  restic -o sftp.command="ssh -p ${BACKUP_SSH_PORTA:-23} -i $SEGRETI/backup_key -o UserKnownHostsFile=$SEGRETI/known_hosts -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ServerAliveInterval=30 $utente_host -s sftp" "$@"
}
