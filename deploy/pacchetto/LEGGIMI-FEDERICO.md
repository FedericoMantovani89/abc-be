# Come preparare il pacchetto per ABC (giorno del rilascio)

_Per Federico. Il pacchetto **non** e' stato preparato: qui c'e' solo lo script che lo prepara quando lo lanci tu._

## Prima di lanciarlo

1. **Tutte le modifiche al codice sono finite e unite su `main`**, in tutti e due i repository (`abc-be` e `abc-fe`).
2. **Repository puliti:** nessuna modifica in sospeso e nessun file non tracciato (`git status` vuoto) su entrambi.
3. **Crea il tag della versione sul commit di `main`** in tutti e due i repository (il nome e' lo stesso, per esempio `v1.1.0`):
   ```powershell
   git -C D:\workspaces\abc-website\abc-be tag v1.1.0
   git -C D:\workspaces\abc-website\abc-fe tag v1.1.0
   ```
   (non serve pushare i tag per fare il pacchetto)
4. **Docker Desktop acceso**, con rete (deve scaricare `caddy` e `postgres` e le dipendenze per costruire).
5. **Dati di ABC nel sito:** nessuno serve. Lo script non legge nessun database, nessun `.env`, nessuna cartella `running\`.
6. Facoltativo: scrivi le note di questa versione in un file `.md` e passalo con `-NoteDiRilascio`. Senza, le note si generano dai messaggi dei commit.

## Provare a secco (consigliato, non costruisce niente)

```powershell
cd D:\workspaces\abc-website\abc-be
powershell -ExecutionPolicy Bypass -File .\deploy\pacchetto\crea-pacchetto.ps1 -Versione v1.1.0 -ProvaASecco
```

Fa solo i controlli (in sola lettura) e stampa tutto cio' che farebbe. Se trova problemi li elenca in rosso e finisce con errore; se e' tutto a posto dice "nessun problema trovato".

## Lanciarlo davvero

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\pacchetto\crea-pacchetto.ps1 -Versione v1.1.0
```

Opzioni: `-CartellaUscita <cartella>` (cambia dove finisce), `-NoteDiRilascio <file.md>`, `-Sovrascrivi` (rifa' uno zip che esiste gia'), `-RepoBackend`/`-RepoFrontend` (se i repo non sono nelle cartelle solite). Servono 10-30 minuti, di solito per la costruzione delle immagini.

## Cosa controlla (si ferma al primo problema)

- `abc-be` e `abc-fe` **puliti**, sul ramo **`main`**;
- il **tag** esiste in tutti e due ed e' **proprio il commit attuale** di `main` (cosi' il pacchetto contiene esattamente la versione che pensi);
- la cartella di uscita sta **fuori dai repository**;
- **nessun segreto, chiave o dato reale** nei file da consegnare: file `.env` (tranne `.env.esempio` vuoto), chiavi private, `.sql`, `.dump`, `.csv`, database, password o token compilati. Se ne trova uno, **si ferma con errore** e dice quale;
- tutti i file da consegnare esistono (compose, Caddyfile, script, documenti).

Poi costruisce le immagini **dal tag** (non dalla tua cartella di lavoro: esporta il codice con `git archive`, quindi i file ignorati o dimenticati non possono finire dentro), le salva compresse, copia i file, scrive `MANIFEST.txt` (versione, commit dei due repo, data) e `SHA256SUMS.txt` (hash di ogni file), rifa' la ricerca di segreti sul pacchetto finito e crea lo zip.

## Dove finisce il file

In `D:\workspaces\abc-website\appoggio\pacchetto-<versione>\` (fuori dai repository), due file:

- `abc-sito-<versione>-<AAAAMMGG>.zip`: il pacchetto;
- `abc-sito-<versione>-<AAAAMMGG>.zip.sha256.txt`: la sua **impronta SHA-256** (una riga). La stampa anche lo script alla fine.

Dentro lo zip c'e' una cartella `abc-sito-<versione>/` con: `immagini/` (backend, frontend, backup, caddy, postgres), `docker-compose.prod.yml`, `Caddyfile`, `.env.esempio` (tutto vuoto), `config-esempio/`, `caddy/`, `script/`, `INSTALLA.md`, `AGGIORNA.md`, `PERSONALIZZARE.md`, `NOTE-DI-RILASCIO.md`, `MANIFEST.txt`, `SHA256SUMS.txt`, `versione.env`.

## Cosa consegnare ad ABC e cosa no

**Si consegna al Referente tecnico di ABC:**
- lo **zip**;
- l'**impronta SHA-256**, **per un altro canale** (messaggio, telefono): serve per accorgersi se lo zip e' stato alterato o corrotto.

Lo zip non contiene dati ne' segreti: puo' passare per link di scaricamento o chiavetta.

**NON si consegna e non si chiede mai:**
- file `.env` o password (le crea e le custodisce ABC);
- chiavi Google/Facebook, chiavi SSH, password della Storage Box o dei backup;
- l'IP del server, o accessi al server, al database, ai pannelli;
- il codice sorgente (nel pacchetto non c'e'; l'eventuale deposito e' un tema dell'accordo, art. 12).

## Dopo la consegna

- Conserva lo **zip** e la **cartella di uscita**: servono per capire cosa e' installato (`MANIFEST.txt`).
- Se ABC segnala un problema: lo riproduci **sul tuo PC con dati finti**, mai sul loro server.
- Prossima versione: stesso procedimento con un nuovo tag; ABC la installa con `AGGIORNA.md`.

## Cose da ricordare (checklist)

- Dopo il passaggio: cancellare dal tuo PC i dati reali e spegnere il sito di prova pubblico (checklist 8.12-8.14, 0.5).
- Il giorno del rilascio impostare la data "ultimo aggiornamento" della privacy: la imposta da solo `installa.sh` alla data di installazione; se serve un'altra data si cambia `privacyAggiornata` in `/mnt/abc-dati/config/sito.json` (lo fa il Referente).
- Ricontrollare il giorno del rilascio la certificazione Google/Meta (Data Privacy Framework): risposta 7.

## Cosa NON e' stato provato dal vivo (da provare il giorno del rilascio)

Lo script e gli script del server sono stati scritti e controllati solo in modo statico (sintassi, convalida del compose, prova a secco). **Non e' mai stato costruito un pacchetto ne' installato su un server vero.** Va provato: la costruzione delle immagini, lo zip, `prepara-server.sh`, `installa.sh`, `aggiorna.sh`, `torna-indietro.sh`, il backup su Storage Box, `prova-ripristino.sh`, `ripristina.sh`, il registro delle cancellazioni su un database vero, il certificato HTTPS dopo il passaggio del DNS.
