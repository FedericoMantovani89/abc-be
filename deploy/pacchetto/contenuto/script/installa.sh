#!/usr/bin/env bash
# installa.sh — prima installazione del sito sul server (utente "deploy", dopo prepara-server.sh).
#
#   cd abc-sito-vX.Y.Z          # la cartella dove hai decompresso lo zip
#   bash script/installa.sh
#
# Si lancia DUE volte:
#   1a volta: prepara la cartella del sito e crea il file .env da compilare; poi si ferma.
#   2a volta (dopo aver compilato .env): carica le immagini, avvia il sito e crea il database.
#
# Opzioni:  --si   non chiede conferme (per uso automatico)
set -Eeuo pipefail
. "$(dirname "$0")/lib.sh"

[ "${1:-}" = "--si" ] && SI=1 || SI=0

mesi=(gennaio febbraio marzo aprile maggio giugno luglio agosto settembre ottobre novembre dicembre)
data_italiana() { printf '%d %s %d' "$(date +%-d)" "${mesi[$(( $(date +%-m) - 1 ))]}" "$(date +%Y)"; }

passo "1/8 Controlli iniziali"
serve_comando docker
serve_comando sha256sum "Manca sha256sum (pacchetto coreutils)."
docker info >/dev/null 2>&1 || fermati "Docker non risponde. Sei l'utente deploy? Prova a uscire e rientrare con ssh, poi rilancia (il gruppo docker si attiva al login)."
[ -f "$PACCHETTO/versione.env" ] || fermati "Questa non sembra la cartella del pacchetto (manca versione.env)."
[ -d "$PACCHETTO/immagini" ] || fermati "Mancano le immagini nel pacchetto (cartella immagini/)."
if [ -e "$CARTELLA_SITO/.installato" ]; then
  fermati "Il sito e' gia' installato in $CARTELLA_SITO (versione $(versione_installata)). Per passare a una nuova versione usa aggiorna.sh."
fi
VERSIONE="$(versione_pacchetto)"
ok "installo la versione $VERSIONE in $CARTELLA_SITO"
DATI="$(leggi_env "$CARTELLA_SITO/.env" DATI_DIR)"; DATI="${DATI:-/mnt/abc-dati}"
mountpoint -q "$DATI" || avviso "$DATI non e' un Volume separato: i dati finiranno sul disco del server. Va bene solo per una prova."

passo "2/8 Controllo che il pacchetto non sia stato alterato"
controlla_hash_pacchetto

passo "3/8 Preparo la cartella del sito"
mkdir -p "$CARTELLA_SITO"
cp "$PACCHETTO/docker-compose.prod.yml" "$PACCHETTO/Caddyfile" "$PACCHETTO/versione.env" "$PACCHETTO/.env.esempio" "$PACCHETTO/MANIFEST.txt" "$CARTELLA_SITO/"
mkdir -p "$CARTELLA_SITO/caddy/tls" "$CARTELLA_SITO/script" "$CARTELLA_SITO/config-esempio" "$CARTELLA_SITO/segreti"
cp -r "$PACCHETTO/caddy/." "$CARTELLA_SITO/caddy/"
cp -r "$PACCHETTO/script/." "$CARTELLA_SITO/script/"
cp -r "$PACCHETTO/config-esempio/." "$CARTELLA_SITO/config-esempio/"
chmod +x "$CARTELLA_SITO"/script/*.sh "$CARTELLA_SITO/caddy/manutenzione.sh"
# i documenti (PERSONALIZZARE.md rimanda a CHIAVI-DISPONIBILI.md) stanno anche nella cartella del sito
for f in INSTALLA.md AGGIORNA.md PERSONALIZZARE.md CHIAVI-DISPONIBILI.md NOTE-DI-RILASCIO.md; do
  if [ -f "$PACCHETTO/$f" ]; then cp "$PACCHETTO/$f" "$CARTELLA_SITO/"; fi
done
chmod 700 "$CARTELLA_SITO/segreti"
ok "file copiati in $CARTELLA_SITO"

if [ ! -f "$CARTELLA_SITO/.env" ]; then
  cp "$PACCHETTO/.env.esempio" "$CARTELLA_SITO/.env"
  chmod 600 "$CARTELLA_SITO/.env"
  cat <<FINE

== PRIMA TAPPA FINITA. Ora tocca a te compilare i segreti:

     nano $CARTELLA_SITO/.env

   - segui i commenti nel file: i valori segnati con (*) vanno compilati
     (i comandi per generare le password sono scritti accanto);
   - il file resta SOLO su questo server; metti una copia nel gestore di password di ABC;
   - quando hai finito, rilancia:   bash script/installa.sh
FINE
  exit 0
fi

passo "4/8 Controllo il file .env"
chmod 600 "$CARTELLA_SITO/.env"
controlla_env "$CARTELLA_SITO/.env"
ok "i segreti obbligatori ci sono"
DATI="$(dati_dir)"
if [ -z "$(env_sito BACKUP_REPOSITORY)" ] || [ -z "$(env_sito BACKUP_PASSWORD)" ]; then
  avviso "Il backup sulla Storage Box NON e' configurato (BACKUP_REPOSITORY / BACKUP_PASSWORD vuoti)."
  avviso "Il sito parte lo stesso, ma NON e' protetto: configuralo subito dopo (INSTALLA.md, passo Backup)."
fi
case "$(env_sito SMTP_SSL)" in true) ;; *) avviso "SMTP_SSL non e' \"true\": con Aruba (porta 465) deve esserlo." ;; esac
if [ "$(env_sito SMTP_USER)" != "$(env_sito MAIL_FROM)" ]; then
  avviso "MAIL_FROM e SMTP_USER sono diversi: Aruba rifiuta le email con un mittente diverso dalla casella."
fi

passo "5/8 Cartelle dei dati in $DATI"
mkdir -p "$DATI"/{postgres,uploads,config,registro-cancellazioni,copie-aggiornamento,caddy/data,caddy/config}
if [ ! -f "$DATI/config/sito.json" ]; then
  # prima installazione: la configurazione di esempio, con la data di oggi come "ultimo aggiornamento" della privacy
  sed "s/@DATA_INSTALLAZIONE@/$(data_italiana)/" "$CARTELLA_SITO/config-esempio/sito.json" > "$DATI/config/sito.json"
  ok "creato $DATI/config/sito.json (privacy aggiornata il $(data_italiana))"
fi
[ -f "$DATI/config/testi.json" ] || cp "$CARTELLA_SITO/config-esempio/testi.json" "$DATI/config/testi.json"

passo "6/8 Carico le immagini del sito"
carica_immagini
# la cartella dei file caricati deve essere scrivibile dall'utente con cui gira il backend
BE_UID="$(docker run --rm --entrypoint id "abc-musical/backend:$VERSIONE" -u abc)"
BE_GID="$(docker run --rm --entrypoint id "abc-musical/backend:$VERSIONE" -g abc)"
sudo chown -R "$BE_UID:$BE_GID" "$DATI/uploads"
ok "cartella dei file caricati pronta (utente $BE_UID)"

passo "7/8 Avvio il sito"
# Finche' il DNS punta ancora al vecchio sito, Let's Encrypt non puo' dare il certificato:
# si parte con un certificato interno (il browser avvisa), da togliere al passaggio del DNS.
bash "$CARTELLA_SITO/script/https-prova.sh" on --senza-reload
dc config -q
dc up -d
attendi_sito_sano
dc ps

passo "8/8 Registro delle cancellazioni"
bash "$CARTELLA_SITO/script/registro-cancellazioni.sh" aggiorna
date '+%F %T' > "$CARTELLA_SITO/.installato"

cat <<FINE

== INSTALLATO. Versione $VERSIONE.
   Il sito gira, ma il pubblico vede ancora il vecchio sito: il DNS non e' stato toccato.

   PROVA PRIMA DI TOCCARE IL DNS (INSTALLA.md, passo 7):
     sul PC del Referente aggiungi nel file "hosts" la riga:   <IP-di-questo-server>  www.attoriballerinicantanti.it
     poi apri https://www.attoriballerinicantanti.it
     (il browser avvisa che il certificato non e' fidato: e' normale finche' non passi il DNS.
      Al passaggio del DNS, PER PRIMA COSA lancia:  bash script/https-prova.sh off)

   Accedi con ADMIN_EMAIL e ADMIN_PASSWORD scritti nel file .env, poi cambia la password dal sito.
FINE
