#!/usr/bin/env bash
# riavvia.sh — applica una modifica al file .env o ai file in /config.
#
#   bash script/riavvia.sh              riavvia tutto il sito (dopo aver cambiato il file .env)
#   bash script/riavvia.sh frontend     riavvia solo il frontend (dopo testi.json o sito.json)
#   bash script/riavvia.sh backend      riavvia solo il backend (dopo application-prod.yml, messages.properties, email/)
#
# Dopo una modifica al .env il sito viene ricreato con i valori nuovi (docker compose up -d).
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

serve_comando docker
controlla_env "$CARTELLA_SITO/.env"
SERVIZIO="${1:-}"
case "$SERVIZIO" in
  "")
    passo "Applico il file .env e riavvio il sito"
    dc config -q
    dc up -d
    ;;
  frontend|backend|caddy|backup|postgres)
    passo "Riavvio $SERVIZIO"
    dc restart "$SERVIZIO"
    ;;
  *) fermati "Servizio sconosciuto: $SERVIZIO (valide: frontend, backend, caddy, backup, postgres, oppure niente per tutto)." ;;
esac
attendi_sito_sano
