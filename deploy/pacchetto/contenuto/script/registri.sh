#!/usr/bin/env bash
# registri.sh — mostra gli ultimi messaggi dei servizi (per capire cosa non va).
#
#   bash script/registri.sh                  ultimi messaggi di tutti i servizi
#   bash script/registri.sh backend 300      ultimi 300 messaggi del backend
#   bash script/registri.sh backend seguire  li mostra in diretta (Ctrl+C per uscire)
#
# I registri si tengono al massimo 14 giorni. Prima di mandarli a Federico togli password,
# email e indirizzi di persone vere.
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

serve_comando docker
SERVIZIO="${1:-}"
N="${2:-100}"
if [ "$N" = "seguire" ]; then
  dc logs -f --tail 50 ${SERVIZIO:+"$SERVIZIO"}
else
  dc logs --tail "$N" ${SERVIZIO:+"$SERVIZIO"}
fi
