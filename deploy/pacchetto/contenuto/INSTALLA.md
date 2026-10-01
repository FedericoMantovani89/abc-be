# Installare il sito di ABC Musical Company

_Per il Referente tecnico di ABC. Si fa una volta sola, in una sera. Tutti i comandi sono da copiare e incollare._

Cosa serve: un PC con un terminale (Windows PowerShell, oppure Mac/Linux), un server già creato (vedi passo 3) e circa due ore.
**Federico non ha accesso al server né alle password: questo pacchetto lo installate e lo custodite voi.**

## Cosa avete ricevuto

- un file `abc-sito-vX.Y.Z-AAAAMMGG.zip`: è il pacchetto, con i programmi già pronti (non c'è codice sorgente);
- l'**impronta SHA-256** dello zip, una riga di lettere e numeri, mandata a parte (messaggio, telefono, email).

Nello zip **non ci sono né password né dati**: i segreti li create voi, ai passi 6 e 8.

## Passo 1. Controllare che lo zip sia quello giusto

Sul PC del Referente, nella cartella dove hai scaricato lo zip.

Windows (PowerShell):

```powershell
Get-FileHash .\abc-sito-vX.Y.Z-AAAAMMGG.zip -Algorithm SHA256
```

Mac o Linux:

```sh
sha256sum abc-sito-vX.Y.Z-AAAAMMGG.zip
```

Il risultato deve essere **uguale, lettera per lettera**, all'impronta ricevuta. Se è diverso, **non installare**: scarica di nuovo lo zip o chiedi a Federico di rimandarlo.

## Passo 2. La chiave SSH del Referente

La chiave SSH è il "badge" per entrare nel server senza password. Se già ne hai una e la usi per ABC, salta al passo 3.

```sh
ssh-keygen -t ed25519 -C "abc-produzione"
```

- Accetta il percorso proposto e **scrivi una passphrase** (una frase lunga che ricordi).
- Nascono due file: `id_ed25519` (**privato: non si condivide mai**) e `id_ed25519.pub` (pubblico).
- Metti una copia della chiave privata **e** della passphrase nel gestore di password di ABC: se il Referente cambia, l'accesso non si perde.
- Il contenuto del file `.pub` ti serve ai passi 3 e 4. Per vederlo: `cat ~/.ssh/id_ed25519.pub` (su Windows: `type $env:USERPROFILE\.ssh\id_ed25519.pub`).

## Passo 3. Il server

Account Hetzner **intestato ad ABC** (https://console.hetzner.com), poi **Add Server** con queste scelte:

| Voce | Valore |
|---|---|
| Location | Falkenstein o Nuremberg (Germania) |
| Image | Ubuntu 24.04 |
| Type | Shared vCPU x86, `CX33` (4 vCPU, 8 GB), oppure `CX23` per spendere meno |
| Networking | IPv4 e IPv6 |
| SSH key | la tua, dal passo 2 (incolla il contenuto del file `.pub`) |
| Volume | 100 GB, nome `abc-dati`, `ext4`, **montaggio automatico** |
| Firewall | nuovo, in ingresso solo TCP 22, 80, 443 |
| Backups | attivi (copia giornaliera del server) |
| Name | `abc-prod` |

Annota l'indirizzo IP del server (IPv4) nei documenti di ABC.

## Passo 4. Mettere in sicurezza il server (una volta sola)

Copia lo zip sul server (da Windows PowerShell, sostituisci `IP` con l'indirizzo del server):

```sh
scp abc-sito-vX.Y.Z-AAAAMMGG.zip root@IP:/root/
ssh root@IP
```

Ora sei **dentro il server**, come root. Esegui:

```sh
apt-get update && apt-get install -y unzip
unzip abc-sito-vX.Y.Z-AAAAMMGG.zip
cd abc-sito-vX.Y.Z
bash script/prepara-server.sh "ssh-ed25519 AAAA...incolla qui la tua chiave pubblica... abc-produzione"
```

(La chiave pubblica è tra virgolette, tutta su una riga.) Lo script: crea l'utente `deploy`, spegne l'accesso di root e quello con password, accende firewall, aggiornamenti automatici e `fail2ban`, installa Docker, monta il Volume in `/mnt/abc-dati`, imposta il fuso orario di Roma e limita i registri a 14 giorni.

**Prima di chiudere la finestra**, apri un secondo terminale e controlla:

```sh
ssh deploy@IP          # deve entrare
ssh root@IP            # deve essere RIFIUTATO
```

Se `deploy` non entra, correggi dalla prima finestra (ancora aperta). Poi esci con `exit`.

## Passo 5. Portare il pacchetto sull'utente deploy

Da ora si lavora con l'utente `deploy`. Dal tuo PC, copia di nuovo lo zip e decomprimilo:

```sh
scp abc-sito-vX.Y.Z-AAAAMMGG.zip deploy@IP:~/
ssh deploy@IP
unzip abc-sito-vX.Y.Z-AAAAMMGG.zip
cd abc-sito-vX.Y.Z
```

Controlla che Docker funzioni: `docker run --rm hello-world` (deve stampare "Hello from Docker!"). Se dice "permission denied", esci e rientra con `ssh`.

## Passo 6. I segreti (file `.env`)

Prima esecuzione, dalla cartella del pacchetto:

```sh
bash script/installa.sh
```

Lo script controlla il pacchetto, prepara `/opt/abc-sito` e crea il file dei segreti, poi **si ferma** e ti dice di compilarlo:

```sh
nano /opt/abc-sito/.env
```

Il file ha i commenti accanto a ogni valore. In sintesi, compila i valori segnati `(*)`:

| Valore | Cosa scrivere |
|---|---|
| `JWT_SECRET`, `NEXTAUTH_SECRET`, `REMEMBER_ME_KEY` | un valore diverso per ognuno, generato con `openssl rand -base64 48 \| tr -d '\n=+/'` |
| `DB_PASSWORD` | generata con `openssl rand -base64 32 \| tr -d '\n=+/'` |
| `ADMIN_EMAIL` | l'email del **primo amministratore di ABC** (una persona di ABC) |
| `ADMIN_PASSWORD` | generata con `openssl rand -base64 18 \| tr -d '\n=+/'` (la cambierai dal sito) |
| `SMTP_PASSWORD` | la password della casella `info@attoriballerinicantanti.it` (Aruba) |
| `GOOGLE_CLIENT_ID/SECRET`, `FACEBOOK_CLIENT_ID/SECRET` | le chiavi create da ABC (vedi sotto). **Facoltative**: se per ora non le avete, lasciale vuote e il sito parte lo stesso |

Per generare una password: lancia il comando, copia il risultato e incollalo dopo il segno `=`, **senza spazi né virgolette**. Ogni segreto si genera una volta sola e va copiato **subito** nel gestore di password di ABC.

> Nessun segreto passa da Federico, né per email né per chat. Il file `.env` resta solo sul server (e nel gestore di password).

**Posta:** `SMTP_HOST=smtps.aruba.it`, porta `465`, `SMTP_SSL=true`, utente e mittente `info@attoriballerinicantanti.it` sono già scritti. Se cambiate la password della casella `info@`, cambiatela anche qui e lanciate `bash /opt/abc-sito/script/riavvia.sh`.

> **Se Aruba non risponde** (o la password SMTP è sbagliata) il sito **resta in piedi**: partono solo le email (conferma iscrizione, reset password) che non vengono consegnate. Il controllo di salute che usa Docker non include la posta. Per vedere lo stato della posta: `bash /opt/abc-sito/script/compose.sh exec backend wget -qO- http://127.0.0.1:8080/actuator/health/posta` (risponde `UP` o `DOWN`).

**Macchina di prova senza systemd** (per esempio un test dentro un container): i registri in `journald` impediscono l'avvio dei container. Aggiungi nel `.env` `LOG_DRIVER=json-file`. Su un server Ubuntu normale non scrivere nulla (predefinito `journald`).

**Chiavi Google e Facebook** (account di ABC; **facoltative**: si possono fare anche dopo, il sito parte lo stesso):

Un accesso social si attiva solo se per quel servizio sono compilati **sia l'ID sia il segreto**. Senza chiavi quel servizio è semplicemente spento: se qualcuno clicca il suo pulsante torna alla pagina di accesso con un errore chiaro (non un errore del server). All'avvio il backend scrive nel registro quali accessi sono attivi: `bash /opt/abc-sito/script/registri.sh backend | grep "Accessi social"`. Dopo aver aggiunto le chiavi lancia `bash /opt/abc-sito/script/riavvia.sh`.

- *Google* — https://console.cloud.google.com → nuovo progetto → Google Auth Platform: tipo Esterno, dominio `attoriballerinicantanti.it`, ambiti solo `openid email profile`, **pubblica l'app**. Credenziali → ID client OAuth → Applicazione web. Origine JavaScript `https://www.attoriballerinicantanti.it`; URI di reindirizzamento **esatto**: `https://www.attoriballerinicantanti.it/login/oauth2/code/google`.
- *Facebook* — https://developers.facebook.com → nuova app con Facebook Login. URI di reindirizzamento: `https://www.attoriballerinicantanti.it/login/oauth2/code/facebook`; URL privacy `https://www.attoriballerinicantanti.it/privacy-policy`; URL cancellazione dati `https://www.attoriballerinicantanti.it/privacy-policy#cancellazione`; autorizzazioni solo `email` e `public_profile`; app in modalità **Live**.

## Passo 7. Installare e provare il sito (senza toccare il DNS)

Rilancia lo stesso comando:

```sh
bash script/installa.sh
```

Carica le immagini, avvia database, backend, frontend e Caddy, crea il database e l'utente amministratore. Alla fine scrive **INSTALLATO**. Se si ferma con un errore, leggi il messaggio: dice quale passo è fallito.

Il pubblico vede ancora il vecchio sito. Per provare il nuovo dal tuo PC, aggiungi questa riga al file `hosts` (Windows: `C:\Windows\System32\drivers\etc\hosts`, da Blocco note come amministratore; Mac/Linux: `/etc/hosts`):

```
IP-DEL-SERVER  www.attoriballerinicantanti.it
```

Apri `https://www.attoriballerinicantanti.it`: il browser avvisa che il certificato non è fidato (è normale, è il certificato di prova), prosegui. Controlla home, spettacoli, accesso con l'amministratore. **Togli la riga dal file `hosts` quando hai finito.**

**Su una macchina di prova senza dominio** (nessun DNS e nessun file `hosts` da modificare, per esempio un server o una VM di test): il sito risponde comunque, perché è configurato per il nome `www.attoriballerinicantanti.it` con il certificato di prova. Basta dire a `curl` a quale indirizzo andare, **dalla macchina stessa o da un'altra**:

```sh
curl -k --resolve www.attoriballerinicantanti.it:443:IP-DEL-SERVER https://www.attoriballerinicantanti.it/     # la home (-k: accetta il certificato di prova)
```

(su una macchina di prova usa `127.0.0.1` come IP). **Non lanciare `https-prova.sh off` su una macchina di prova**: spegne il certificato interno e Caddy prova a ottenerne uno vero da Let's Encrypt, che fallisce se il DNS del dominio non punta a quella macchina.

## Passo 8. I backup (obbligatorio prima del passaggio)

I backup vanno su una **Storage Box Hetzner** di ABC (BX11, 1 TB, location Helsinki, con **SSH support** attivo), **cifrati**: senza la password di cifratura nessuno li apre, nemmeno Hetzner.

1. Crea una chiave solo per i backup, sul server:
   ```sh
   ssh-keygen -t ed25519 -N "" -C "abc-backup" -f /opt/abc-sito/segreti/backup_key
   ```
2. Installa la parte pubblica sulla Storage Box (chiederà la password della Storage Box, una volta). Sostituisci `uNNNNNN` con il nome utente della Storage Box:
   ```sh
   cat /opt/abc-sito/segreti/backup_key.pub | ssh -p 23 uNNNNNN@uNNNNNN.your-storagebox.de install-ssh-key
   ```
   Se i comandi di Hetzner sono cambiati, segui la loro guida "SSH Keys" della Storage Box: serve solo che la chiave `backup_key.pub` risulti autorizzata.
3. Nel file `/opt/abc-sito/.env` compila:
   ```
   BACKUP_REPOSITORY=sftp:uNNNNNN@uNNNNNN.your-storagebox.de:abc-backup
   BACKUP_PASSWORD=<generata con: openssl rand -base64 32 | tr -d '\n=+/'>
   ```
   **Custodisci `BACKUP_PASSWORD` nel gestore di password di ABC E scritta su carta in sede.** Senza, i backup sono carta straccia e nessuno la può recuperare.
4. Applica e fai subito un primo backup:
   ```sh
   bash /opt/abc-sito/script/riavvia.sh
   bash /opt/abc-sito/script/compose.sh exec backup /backup/backup.sh
   ```
   Alla fine deve scrivere `BACKUP OK`. Da quel momento il backup parte da solo ogni notte alle 03:30 e conserva 7 copie giornaliere, 4 settimanali, 12 mensili.
5. **Prova di ripristino (obbligatoria, da ripetere ogni 3 mesi):** su un server usa e getta (o anche su quello vero, non rischia nulla) lancia `bash script/prova-ripristino.sh`. Scarica l'ultimo backup in un database temporaneo, lo controlla e stampa **PROVA DI RIPRISTINO RIUSCITA**. Segna la data nei documenti di ABC. Se fallisce, i backup non sono affidabili: non ignorarla, scrivi a Federico.

> **Attenzione ai dati personali nei backup:** una persona che chiede la cancellazione dei suoi dati sparisce dal sito subito, ma dai backup solo alla loro scadenza (fino a 12 mesi). Se un backup viene ripristinato, le cancellazioni chieste nel frattempo vengono **rifatte** automaticamente da `ripristina.sh`: il registro è in `/mnt/abc-dati/registro-cancellazioni/`, contiene solo numeri e non va cancellato.

## Passo 9. Il passaggio del DNS (il giorno del rilascio)

Due giorni prima: scarica il vecchio sito da Aruba (File Manager o FTP), fai uno **screenshot di tutti i record DNS** (è il piano B) e abbassa il TTL dei record `A` di `@` e `www` a 300 secondi.

Il giorno del passaggio, nel pannello Aruba → Gestione DNS cambia **solo**:

- `A` di `attoriballerinicantanti.it` (`@`) → IP del server;
- `A` di `www` → IP del server (se è un CNAME, trasformalo in `A`);
- facoltativo: `AAAA` → IPv6 del server.

**Non toccare** `MX`, `TXT` (SPF, DKIM), `mail`, `pec`, `autodiscover`, `smtp`, `imap`, `pop`, `webmail`.

**Subito dopo**, sul server, spegni il certificato di prova:

```sh
bash /opt/abc-sito/script/https-prova.sh off
```

> **Attenzione:** `https-prova.sh off` fa contattare a Caddy il **vero Let's Encrypt**. Se il DNS **non** punta già a questo server il certificato non arriva e il sito dà errore di certificato: lancialo solo **dopo** aver cambiato il DNS (e dopo che il cambio si è propagato). Se l'hai lanciato troppo presto, rimetti la prova con `bash /opt/abc-sito/script/https-prova.sh on`.

Caddy chiede da solo il certificato vero (Let's Encrypt): serve qualche minuto. Controlla:

```sh
curl -sI https://www.attoriballerinicantanti.it | head -n 1      # deve dire HTTP/2 200 (o 2xx), senza avvisi
```

Controlli finali (da telefono con rete dati e da PC): `https://attoriballerinicantanti.it` rimanda a `https://www...` con il lucchetto; home, spettacoli, eventi, galleria, privacy; iscrizione → email (non nello spam) → conferma; password dimenticata → email → reset; accesso con Google e con Facebook (con un account non tester); caricamento di un PDF, una foto e un video dall'area admin; un socio vede solo le cartelle permesse; posta normale e PEC arrivano ancora. Dopo 48 ore tranquille riporta il TTL al valore normale.

## Personalizzare testi e dati dell'associazione

Si fa **senza ricompilare**, con file di testo: vedi `PERSONALIZZARE.md` (nel pacchetto). I file stanno in `/mnt/abc-dati/config` (`testi.json`, `sito.json`). Per applicare una modifica ai testi: `bash /opt/abc-sito/script/riavvia.sh frontend`. (Dove `PERSONALIZZARE.md` scrive "docker compose restart frontend", su questo server si usa quel comando.) Le modifiche **non** si perdono con gli aggiornamenti.

Il numero RUNTS di ABC è già impostato (2716 del 04/07/2022). Per cambiarlo scrivi il nuovo valore in `sito.json` (`"runts"`) e in `.env` (`ASSOCIAZIONE_RUNTS`) e riavvia tutto con `riavvia.sh`.

## Comandi di tutti i giorni

Dalla cartella `/opt/abc-sito`:

| Cosa | Comando |
|---|---|
| Stato dei servizi | `bash script/compose.sh ps` |
| Ultimi messaggi (per capire un problema) | `bash script/registri.sh backend` (o `frontend`, `caddy`, `backup`) |
| Qualunque comando `docker compose` | `bash script/compose.sh <comando>` (usa da solo `.env` e la versione) |
| Riavviare tutto dopo aver cambiato `.env` | `bash script/riavvia.sh` |
| Accendere/spegnere a mano la pagina "Torniamo tra poco" | `sh caddy/manutenzione.sh on` / `off` / `stato` |
| Nuova versione | vedi `AGGIORNA.md` |

I registri dei servizi contengono indirizzi IP ed email e il server li conserva al massimo **14 giorni**.

## Se il server si rompe o viene perso

1. Crea un server nuovo come ai passi 3-5 e lancia `prepara-server.sh`.
2. Usa lo **stesso zip** (o l'ultimo), lancia `installa.sh` una prima volta, e al posto del `.env` vuoto rimetti **il vostro `.env`** (dal gestore di password) in `/opt/abc-sito/.env`. Rimetti anche la chiave `backup_key` in `/opt/abc-sito/segreti/` (la copia sta nel gestore di password: salvala lì al passo 8).
3. Rilancia `installa.sh` (parte vuoto) e poi: `bash /opt/abc-sito/script/ripristina.sh`. Scarica l'ultimo backup, ripristina database, file caricati e configurazione, e rifà le cancellazioni degli utenti.
4. Sposta il DNS sul nuovo server (passo 9).

## Se il server è del tutto spento

La pagina "Torniamo tra poco" la mostra il server stesso: se è **del tutto** spento nessuno risponde. Con Hetzner è raro; di solito basta aspettare il ripristino (minuti). Piano B facoltativo: se tenete il pacchetto hosting Aruba, caricateci una pagina statica e in emergenza spostate i record `A` sull'IP dell'hosting Aruba (e poi riportateli indietro): è lento, dipende dal TTL.

## Segnalare un problema a Federico

Scrivi: cosa stavi facendo, in quale pagina, a che ora, da telefono o PC, e uno screenshot. **Senza dati personali**: niente nomi, email o documenti di persone vere (oscurali). Se copi messaggi di `registri.sh`, togli password, email e indirizzi IP prima di inviarli. Federico riproduce il problema sul suo PC con dati finti: non ha e non deve avere accessi al server.

## Cose da non fare

- Non mandare a nessuno (Federico compreso) il file `.env`, le password o le chiavi.
- Non cancellare `/mnt/abc-dati`: ci sono database e file caricati.
- Non cancellare `/mnt/abc-dati/registro-cancellazioni`.
- Non cambiare a mano `JWT_SECRET` e gli altri segreti dopo l'installazione: tutti gli utenti verrebbero scollegati; `DB_PASSWORD` non si cambia dal solo `.env`.
- Non aprire sul firewall porte oltre 22, 80 e 443.
