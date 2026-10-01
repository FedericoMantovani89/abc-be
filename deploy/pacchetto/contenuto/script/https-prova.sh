#!/usr/bin/env bash
# https-prova.sh — certificato HTTPS "di prova" per vedere il sito PRIMA di spostare il DNS.
#
#   bash script/https-prova.sh on      usa un certificato interno (il browser avvisa: e' normale)
#   bash script/https-prova.sh off     torna al certificato vero di Let's Encrypt  <- da fare al passaggio del DNS
#   bash script/https-prova.sh stato
#
# Perche': Let's Encrypt controlla che il dominio punti a questo server. Finche' il DNS punta ancora
# al vecchio sito il certificato vero non puo' arrivare.
# Va in funzione senza fermare il sito (Caddy ricarica la configurazione).
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

FILE="$CARTELLA_SITO/caddy/tls/prova.caddy"
mkdir -p "$CARTELLA_SITO/caddy/tls"
RELOAD=1
[ "${2:-}" = "--senza-reload" ] && RELOAD=0

ricarica() {
  if [ "$RELOAD" = "1" ] && [ -n "$(dc ps -q caddy 2>/dev/null)" ]; then
    dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile
  fi
}

case "${1:-}" in
  on)
    printf 'tls internal\n' > "$FILE"
    ricarica
    echo "HTTPS di PROVA acceso: il sito usa un certificato interno (il browser avvisa)."
    echo "Quando sposti il DNS, lancia:  bash script/https-prova.sh off"
    ;;
  off)
    rm -f "$FILE"
    ricarica
    echo "HTTPS di prova spento: Caddy chiede il certificato vero a Let's Encrypt (serve che il DNS punti a questo server)."
    echo "Controlla tra qualche minuto:  curl -sI https://www.attoriballerinicantanti.it | head -n 1"
    ;;
  stato)
    if [ -e "$FILE" ]; then echo "HTTPS di PROVA ACCESO (certificato interno)"; else echo "HTTPS normale (Let's Encrypt)"; fi
    ;;
  *) echo "uso: $0 on|off|stato" >&2; exit 2 ;;
esac
