# Aggiornare il sito a una nuova versione

_Per il Referente tecnico di ABC. Dura circa 10-15 minuti; in questo tempo il pubblico vede la pagina "Torniamo tra poco"._

Quando Federico consegna una nuova versione ricevi: il nuovo zip `abc-sito-vX.Y.Z-AAAAMMGG.zip`, la sua **impronta SHA-256** e le **note di rilascio** (`NOTE-DI-RILASCIO.md`, dentro lo zip).

## Prima di cominciare

1. **Leggi le note di rilascio.** Se c'è scritto in grassetto **"questa versione CAMBIA IL DATABASE"**, tornare indietro vorrà dire ripristinare la copia fatta all'inizio dell'aggiornamento: gli iscritti e i contenuti inseriti *dopo* andrebbero persi. In quel caso scegli un'ora tranquilla e non lasciare a metà.
2. Scegli un momento con poco traffico (sera, non di venerdì).
3. Controlla che il backup notturno sia andato bene: `bash /opt/abc-sito/script/registri.sh backup 30` deve mostrare `BACKUP OK` di stanotte.

## Passi

**1. Controlla l'impronta dello zip** (come al passo 1 di `INSTALLA.md`): deve essere uguale a quella ricevuta.

**2. Copialo sul server e decomprimilo in una cartella NUOVA** (non sovrascrivere la vecchia):

```sh
scp abc-sito-vX.Y.Z-AAAAMMGG.zip deploy@IP:~/
ssh deploy@IP
unzip abc-sito-vX.Y.Z-AAAAMMGG.zip
cd abc-sito-vX.Y.Z
```

**3. Lancia l'aggiornamento:**

```sh
bash script/aggiorna.sh
```

Lo script, in ordine e **fermandosi al primo errore**:

1. controlla che il pacchetto sia intatto e che la versione sia diversa da quella installata;
2. ti chiede conferma e **accende la manutenzione** (il pubblico vede "Torniamo tra poco");
3. fa un **backup completo**: una copia del database sul server (in `/mnt/abc-dati/copie-aggiornamento/`) e un backup sulla Storage Box;
4. tiene da parte la **versione precedente**;
5. carica e avvia la nuova versione; il database si aggiorna da solo;
6. controlla che backend e frontend rispondano e **spegne la manutenzione**.

**4. Controlla il sito:** home, accesso, un caricamento di prova. Alla fine dello script legge `AGGIORNATO a vX.Y.Z`.

I tuoi **testi e dati personalizzati** (cartella `/mnt/abc-dati/config`) e il file `.env` **non vengono toccati**.

Dopo l'aggiornamento puoi cancellare la cartella del vecchio pacchetto e lo zip dalla tua cartella personale.

## Se qualcosa non va: tornare indietro

Se lo script si ferma con un errore **prima di sostituire i file del sito** (passi 1-6: controlli, backup, immagini), il sito è ancora la versione di prima: lo script **spegne da solo la manutenzione** e te lo dice. Se si ferma **dopo** (passi 7-9) la manutenzione resta accesa (così nessuno vede un sito a metà) e lo script scrive **in una riga** il comando da lanciare: `torna-indietro.sh`, oppure `sh /opt/abc-sito/caddy/manutenzione.sh off` se il sito è sano. Se invece l'aggiornamento è finito ma noti un problema grave:

```sh
bash /opt/abc-sito/script/torna-indietro.sh
```

- Se la nuova versione **non** cambiava il database: rimette il programma precedente e i dati restano quelli di adesso.
- Se la nuova versione **cambiava** il database: lo script spiega che si ripristina la copia fatta prima dell'aggiornamento, **perdendo** quanto inserito dopo, e ti chiede di scrivere `TORNA-INDIETRO` per confermare. Le cancellazioni di account chieste nel frattempo vengono rifatte.

Si può tornare indietro solo all'**ultima** versione installata prima di questa (il server tiene le immagini delle due ultime versioni e le ultime 3 copie del database degli aggiornamenti).

Poi scrivi a Federico cosa non andava (pagina, ora, cosa vedevi, **senza dati personali**).

## Altre operazioni

| Cosa | Comando |
|---|---|
| Manutenzione a mano | `sh /opt/abc-sito/caddy/manutenzione.sh on` / `off` / `stato` |
| Che versione è installata | `cat /opt/abc-sito/versione.env` |
| Riavviare dopo aver cambiato `.env` | `bash /opt/abc-sito/script/riavvia.sh` |
| Riavviare dopo aver cambiato i testi | `bash /opt/abc-sito/script/riavvia.sh frontend` |
| Messaggi dei servizi | `bash /opt/abc-sito/script/registri.sh backend` |

## Ogni 3 mesi

Prova di ripristino dei backup: `bash /opt/abc-sito/script/prova-ripristino.sh` (vedi `INSTALLA.md`, passo 8). Segna la data nei documenti di ABC.
