#!/usr/bin/env bash
# compose.sh — scorciatoia per docker compose sul sito installato (usa da solo .env e versione.env).
#
#   bash /opt/abc-sito/script/compose.sh ps                         stato dei servizi
#   bash /opt/abc-sito/script/compose.sh exec backup /backup/backup.sh   backup subito
#   bash /opt/abc-sito/script/compose.sh restart frontend
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"
serve_comando docker
dc "$@"
