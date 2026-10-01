#!/bin/sh
# Interruttore di manutenzione del sito: crea o toglie il file pagine/MANUTENZIONE letto da Caddy.
# Uso: manutenzione.sh on | off | stato      (nessun riavvio: Caddy guarda il file a ogni richiesta)
cd "$(dirname "$0")" || exit 1
FILE=pagine/MANUTENZIONE
case "$1" in
  on)  : > "$FILE" && echo "manutenzione ACCESA: il sito mostra la pagina 'non disponibile' (503)" ;;
  off) rm -f "$FILE" && echo "manutenzione spenta: il sito risponde normalmente" ;;
  stato) if [ -e "$FILE" ]; then echo "manutenzione ACCESA"; else echo "manutenzione spenta"; fi ;;
  *) echo "uso: $0 on|off|stato" >&2; exit 2 ;;
esac
