#!/usr/bin/env bash
# aggiorna.sh — passa il sito a una NUOVA versione del pacchetto.
#
#   1. decomprimi il nuovo zip in una cartella nuova (per esempio ~/abc-sito-v1.2.0)
#   2. cd ~/abc-sito-v1.2.0
#   3. bash script/aggiorna.sh
#
# Cosa fa, nell'ordine (si ferma al primo errore):
#   - controlla che il pacchetto sia intatto e sia piu' nuovo di quello installato
#   - accende la manutenzione (il pubblico vede "Torniamo tra poco")
#   - fa un backup completo: copia del database sul server + backup sulla Storage Box
#   - tiene da parte la versione precedente (per torna-indietro.sh)
#   - carica e avvia la nuova versione; il database si aggiorna da solo
#   - se tutto risponde, spegne la manutenzione
# Se qualcosa va storto la manutenzione RESTA accesa e lo script dice come tornare indietro.
#
# Opzioni:  --si      non chiede conferme
#           --forza   permette di reinstallare la stessa versione
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

SI=0; FORZA=0
for a in "$@"; do
  case "$a" in
    --si) SI=1 ;;
    --forza) FORZA=1 ;;
    *) fermati "Opzione sconosciuta: $a (valide: --si, --forza)" ;;
  esac
done

passo "1/9 Controlli iniziali"
serve_comando docker
docker info >/dev/null 2>&1 || fermati "Docker non risponde. Sei l'utente deploy?"
[ -e "$CARTELLA_SITO/.installato" ] || fermati "Il sito non risulta installato in $CARTELLA_SITO: per la prima installazione usa installa.sh."
[ "$PACCHETTO" != "$CARTELLA_SITO" ] || fermati "Lancia aggiorna.sh dalla cartella del NUOVO pacchetto (quella dove hai decompresso lo zip), non da $CARTELLA_SITO."
controlla_env "$CARTELLA_SITO/.env"
VECCHIA="$(versione_installata)"
NUOVA="$(versione_pacchetto)"
[ -n "$NUOVA" ] || fermati "Nel pacchetto manca la versione (versione.env)."
if [ "$VECCHIA" = "$NUOVA" ] && [ "$FORZA" != "1" ]; then
  fermati "La versione $NUOVA e' gia' installata. Se vuoi comunque rifare l'installazione usa --forza."
fi
CAMBIA_DB="$(leggi_env "$PACCHETTO/versione.env" CAMBIA_DATABASE)"
DATI="$(dati_dir)"
ok "da $VECCHIA a $NUOVA"
if [ "$CAMBIA_DB" = "si" ]; then
  avviso "QUESTA VERSIONE CAMBIA IL DATABASE. Tornare indietro vorra' dire ripristinare la copia fatta ORA:"
  avviso "gli iscritti e i contenuti inseriti DOPO l'aggiornamento andrebbero persi. Leggi NOTE-DI-RILASCIO.md."
fi
if bash "$CARTELLA_SITO/script/https-prova.sh" stato | grep -q PROVA; then
  avviso "L'HTTPS di PROVA e' ancora acceso (certificato interno). Se il DNS e' gia' passato, lancia: bash script/https-prova.sh off"
fi

passo "2/9 Controllo che il pacchetto non sia stato alterato"
controlla_hash_pacchetto

chiedi_si "Aggiorno il sito da $VECCHIA a $NUOVA? Per qualche minuto il pubblico vedra' la pagina 'Torniamo tra poco'."

passo "3/9 Accendo la manutenzione"
manutenzione on
# da qui in poi, se qualcosa fallisce, la manutenzione resta accesa di proposito
trap 'errore "Aggiornamento INTERROTTO al passo \"${PASSO_CORRENTE}\" (riga $LINENO). La manutenzione resta accesa."; errore "Per tornare alla versione precedente:  bash '"$CARTELLA_SITO"'/script/torna-indietro.sh"; exit 1' ERR

passo "4/9 Backup prima di aggiornare"
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" aggiorna
COPIA="$DATI/copie-aggiornamento/$(date +%Y%m%d-%H%M)_da-${VECCHIA}"
mkdir -p "$COPIA"
dc exec -T postgres pg_dump -Fc --no-owner -U "$(env_sito DB_USER)" -d "$(env_sito DB_NAME)" > "$COPIA/abc.dump"
[ -s "$COPIA/abc.dump" ] || fermati "La copia del database e' vuota: non vado avanti."
chmod 600 "$COPIA/abc.dump"
ok "copia del database sul server: $COPIA/abc.dump ($(du -h "$COPIA/abc.dump" | cut -f1))"
if [ -n "$(env_sito BACKUP_REPOSITORY)" ]; then
  dc exec -T backup /backup/backup.sh
  ok "backup sulla Storage Box fatto"
else
  avviso "Backup sulla Storage Box NON configurato: esiste solo la copia sul server."
  chiedi_si "Vuoi andare avanti lo stesso?"
fi

passo "5/9 Tengo da parte la versione precedente"
PREC="$CARTELLA_SITO/precedente"
rm -rf "$PREC"; mkdir -p "$PREC/caddy"
cp "$CARTELLA_SITO/docker-compose.prod.yml" "$CARTELLA_SITO/Caddyfile" "$CARTELLA_SITO/versione.env" "$PREC/"
cp "$CARTELLA_SITO/caddy/abc-sito.caddy" "$PREC/caddy/"
cat > "$CARTELLA_SITO/ultimo-aggiornamento.env" <<RIGHE
VERSIONE_PRECEDENTE=$VECCHIA
VERSIONE_NUOVA=$NUOVA
COPIA_DATABASE=$COPIA/abc.dump
CAMBIA_DATABASE=${CAMBIA_DB:-no}
RIGHE
ok "versione precedente ($VECCHIA) salvata in $PREC"

passo "6/9 Carico la nuova versione"
carica_immagini

passo "7/9 Sostituisco i file del sito"
cp "$PACCHETTO/docker-compose.prod.yml" "$PACCHETTO/Caddyfile" "$PACCHETTO/versione.env" "$PACCHETTO/.env.esempio" "$PACCHETTO/MANIFEST.txt" "$CARTELLA_SITO/"
cp "$PACCHETTO/caddy/abc-sito.caddy" "$CARTELLA_SITO/caddy/"
cp "$PACCHETTO/caddy/manutenzione.sh" "$CARTELLA_SITO/caddy/"
cp -r "$PACCHETTO/caddy/pagine/." "$CARTELLA_SITO/caddy/pagine/"      # il file MANUTENZIONE non e' nel pacchetto: resta com'e'
cp -r "$PACCHETTO/script/." "$CARTELLA_SITO/script/"
cp -r "$PACCHETTO/config-esempio/." "$CARTELLA_SITO/config-esempio/"
for f in INSTALLA.md AGGIORNA.md PERSONALIZZARE.md NOTE-DI-RILASCIO.md; do
  [ -f "$PACCHETTO/$f" ] && cp "$PACCHETTO/$f" "$CARTELLA_SITO/"
done
chmod +x "$CARTELLA_SITO"/script/*.sh "$CARTELLA_SITO/caddy/manutenzione.sh"
ok "la tua cartella /config (testi e dati personalizzati) NON e' stata toccata"

passo "8/9 Avvio la nuova versione"
dc config -q
dc up -d --remove-orphans
attendi_sito_sano
dc ps

passo "9/9 Pulizia e fine manutenzione"
# si tengono solo le immagini della versione nuova e di quella precedente
docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^abc-musical/' \
  | grep -vE ":(${NUOVA}|${VECCHIA})$" | xargs -r docker rmi || true
# si tengono solo le ultime 3 copie del database fatte dagli aggiornamenti
ls -1dt "$DATI"/copie-aggiornamento/*/ 2>/dev/null | tail -n +4 | xargs -r rm -rf
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" stato
manutenzione off
trap '_se_errore $LINENO' ERR

cat <<FINE

== AGGIORNATO a $NUOVA.
   Controlla il sito (home, accesso, un caricamento) e leggi NOTE-DI-RILASCIO.md.
   Se qualcosa non va:   bash $CARTELLA_SITO/script/torna-indietro.sh
FINE
