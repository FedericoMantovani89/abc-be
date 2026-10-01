#!/usr/bin/env bash
# torna-indietro.sh — rimette il sito alla versione di PRIMA dell'ultimo aggiornamento.
#
#   bash script/torna-indietro.sh                  (dalla cartella /opt/abc-sito)
#
# Due casi:
#   - l'ultimo aggiornamento NON cambiava il database: si rimette solo il programma precedente,
#     i dati (iscritti, contenuti) restano quelli di adesso;
#   - l'ultimo aggiornamento CAMBIAVA il database (lo dicono le note di rilascio): serve per forza
#     ripristinare la copia fatta da aggiorna.sh PRIMA di aggiornare. Tutto cio' che e' stato inserito
#     DOPO l'aggiornamento (nuove iscrizioni, caricamenti, modifiche) va PERSO. Le cancellazioni di
#     account chieste nel frattempo vengono rifatte (registro delle cancellazioni).
#
# Opzione:  --con-database   ripristina la copia del database anche se l'aggiornamento non lo cambiava
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

CON_DB=0
for a in "$@"; do
  case "$a" in
    --con-database) CON_DB=1 ;;
    --si) SI=1 ;;
    *) fermati "Opzione sconosciuta: $a (valide: --con-database, --si)" ;;
  esac
done
SI="${SI:-0}"

passo "1/6 Controlli"
serve_comando docker
INFO="$CARTELLA_SITO/ultimo-aggiornamento.env"
[ -f "$INFO" ] || fermati "Non trovo $INFO: non risulta nessun aggiornamento da annullare."
[ -d "$CARTELLA_SITO/precedente" ] || fermati "Manca la cartella 'precedente' in $CARTELLA_SITO: non posso tornare indietro."
VERSIONE_PREC="$(leggi_env "$INFO" VERSIONE_PRECEDENTE)"
VERSIONE_NUOVA="$(leggi_env "$INFO" VERSIONE_NUOVA)"
COPIA="$(leggi_env "$INFO" COPIA_DATABASE)"
CAMBIA="$(leggi_env "$INFO" CAMBIA_DATABASE)"
[ "$CAMBIA" = "si" ] && CON_DB=1
for img in backend frontend backup; do
  docker image inspect "abc-musical/$img:$VERSIONE_PREC" >/dev/null 2>&1 \
    || fermati "L'immagine $img della versione $VERSIONE_PREC non c'e' piu' sul server: per tornare indietro serve di nuovo il vecchio pacchetto (installa le sue immagini con docker load)."
done
ok "da $VERSIONE_NUOVA torno a $VERSIONE_PREC"
if [ "$CON_DB" = "1" ]; then
  [ -s "$COPIA" ] || fermati "La copia del database fatta prima dell'aggiornamento non c'e' piu': $COPIA"
  avviso "SI RIPRISTINA IL DATABASE com'era prima dell'aggiornamento ($COPIA)."
  avviso "Tutto cio' che e' stato inserito DOPO l'aggiornamento andra' PERSO."
  chiedi_parola "TORNA-INDIETRO" "Confermi di tornare a $VERSIONE_PREC PERDENDO i dati nuovi?"
else
  chiedi_si "Torno alla versione $VERSIONE_PREC (i dati restano quelli di adesso)?"
fi

passo "2/6 Accendo la manutenzione"
manutenzione on

passo "3/6 Registro delle cancellazioni"
# prima di sostituire il database si salvano nel registro le cancellazioni chieste dopo l'aggiornamento
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" aggiorna

if [ "$CON_DB" = "1" ]; then
  passo "4/6 Ripristino il database"
  ripristina_database "$COPIA"
else
  passo "4/6 Database: lo lascio com'e'"
fi

passo "5/6 Rimetto i file della versione precedente"
P="$CARTELLA_SITO/precedente"
cp "$P/docker-compose.prod.yml" "$P/Caddyfile" "$P/versione.env" "$CARTELLA_SITO/"
cp "$P/caddy/abc-sito.caddy" "$CARTELLA_SITO/caddy/"
ok "versione in uso: $(versione_installata)"
dc config -q
dc up -d --remove-orphans

passo "6/6 Controllo e fine manutenzione"
attendi_sito_sano
mv "$INFO" "$CARTELLA_SITO/ultimo-aggiornamento.annullato.env"
manutenzione off

cat <<FINE

== TORNATO a $VERSIONE_PREC.
   Scrivi a Federico cosa non andava nella $VERSIONE_NUOVA (pagina, ora, cosa vedevi, senza dati personali).
FINE
