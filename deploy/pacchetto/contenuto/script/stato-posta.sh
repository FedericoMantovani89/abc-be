#!/usr/bin/env bash
# stato-posta.sh — dice se la posta (SMTP) del sito e' raggiungibile. Non modifica nulla.
#
#   bash /opt/abc-sito/script/stato-posta.sh
#
# Scrive "posta: raggiungibile" oppure "posta: NON raggiungibile" e basta: una posta giu' e' un
# risultato, non un errore dello script (il sito resta in piedi, partono solo le email).
# Codice di uscita: 0 = raggiungibile, 1 = NON raggiungibile, 2 = il backend stesso non risponde.
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

serve_comando docker
URL=http://127.0.0.1:8080/actuator/health

if dc exec -T backend wget -q -O /dev/null "$URL/posta" 2>/dev/null; then
  echo "posta: raggiungibile"
  exit 0
fi
# wget esce con errore anche quando il backend risponde 503 (posta giu'): si distingue guardando il sito.
if dc exec -T backend wget -q -O /dev/null "$URL/sito" 2>/dev/null; then
  echo "posta: NON raggiungibile (il sito invece funziona; partono solo le email). Controlla SMTP_* nel .env e la casella su Aruba."
  exit 1
fi
echo "Il backend non risponde: non posso dire nulla sulla posta. Guarda lo stato con:  bash $CARTELLA_SITO/script/compose.sh ps" >&2
exit 2
