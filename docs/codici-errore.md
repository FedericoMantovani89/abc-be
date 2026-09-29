# Codici di errore delle API

Ogni risposta di errore ha la forma `{"error": "<messaggio>", "code": "<codice>"}` (la validazione dei campi aggiunge `"fields": {campo: messaggio italiano}` e `"fieldCodes": {campo: codice}`). `error` e' il messaggio italiano di sempre; `code` e' il codice stabile: il frontend mostra il testo che corrisponde al codice.

- I testi stanno in `src/main/resources/messages.properties` (uno per codice). `{0}`, `{1}`... sono i valori inseriti nel testo.
- Un testo si puo' cambiare sul server copiando la riga in `/config/messages.properties` e riavviando. Nei testi con `{0}` l'apostrofo va raddoppiato (`''`).
- I codici non cambiano mai: se un testo cambia, il codice resta. Un codice nuovo si aggiunge sia a `messages.properties` sia a questo file (un test controlla che coincidano).
- La risposta di login (`POST /api/auth/login`) tiene anche `"success": false` e in `error` i valori tecnici `INVALID_CREDENTIALS` / `ACCOUNT_NOT_VERIFIED`, piu' `code`.

## Account e accesso

| Codice | HTTP | Messaggio |
|---|---|---|
| `auth.email.gia.registrata` | 409 | Email già registrata |
| `auth.password.attuale.errata` | 400 | Password attuale non corretta |
| `auth.utente.non.identificabile` | 400 | Utente non identificabile |
| `auth.account.disattivato` | 400 | Account disattivato |
| `auth.account.non.verificato` | 401 | Account non verificato |
| `auth.credenziali.non.valide` | 401 | Credenziali non valide |
| `auth.troppe.richieste` | 429 | Too many requests. Riprova tra qualche minuto. |
| `auth.token.non.valido` | 400 | Token non valido |
| `auth.token.gia.utilizzato` | 400 | Token già utilizzato |
| `auth.token.scaduto` | 400 | Token scaduto |
| `utente.non.trovato` | 404 | Utente non trovato |

## Utenti (pannello admin)

| Codice | HTTP | Messaggio |
|---|---|---|
| `utenti.pagina.negativa` | 400 | Il numero di pagina non puo' essere negativo |
| `utenti.dimensione.pagina` | 400 | La dimensione della pagina deve essere fra 1 e {0} |
| `utenti.mai.entrato.con.intervallo` | 400 | 'Mai entrato' non si combina con un intervallo di ultimo accesso |
| `utenti.intervallo.date.invertito` | 400 | La data iniziale dell'ultimo accesso e' dopo quella finale |
| `utenti.ruolo.god.non.assegnabile` | 400 | Il ruolo GOD non è assegnabile |
| `utenti.account.tecnico` | 409 | L'account tecnico non è modificabile |
| `utenti.data.non.valida` | 400 | Parametro '{0}' non valido: data attesa AAAA-MM-GG |
| `ruolo.non.valido` | 400 | Ruolo non valido: {0} |
| `ruolo.non.trovato` | 404 | Ruolo non trovato: {0} |

## Calendario

| Codice | HTTP | Messaggio |
|---|---|---|
| `calendario.evento.non.trovato` | 404 | Evento calendario non trovato |
| `calendario.tipo.non.trovato` | 404 | Tipo evento non trovato |
| `calendario.fine.prima.inizio` | 400 | La data di fine precede la data di inizio |
| `calendario.ruoli.senza.spettacolo` | 400 | I ruoli della prova richiedono uno spettacolo |
| `calendario.ruolo.non.nel.cast` | 400 | Il ruolo "{0}" non esiste nel cast dello spettacolo |

## Spettacoli, eventi, comunicazioni

| Codice | HTTP | Messaggio |
|---|---|---|
| `spettacolo.non.trovato` | 404 | Spettacolo non trovato |
| `evento.non.trovato` | 404 | Evento non trovato |
| `evento.tipo.non.trovato` | 404 | Tipo evento non trovato |
| `evento.origine.non.trovato` | 400 | Evento di origine non trovato |
| `evento.prenotazioni.apertura.dopo.chiusura` | 400 | L'apertura delle prenotazioni deve essere precedente alla chiusura. |
| `evento.prenotazioni.chiusura.dopo.evento` | 400 | La chiusura delle prenotazioni non può essere successiva alla data dell'evento. |
| `comunicazione.non.trovata` | 404 | Comunicazione non trovata |
| `comunicazione.tipo.non.trovato` | 404 | Tipo comunicazione non trovato |
| `hero.punto.focale.non.valido` | 400 | Punto focale non valido |
| `hero.zoom.non.valido` | 400 | Zoom non valido |
| `email.locandina.non.disponibile` | 404 | Locandina non disponibile |
| `email.locandina.variante.non.valida` | 400 | Parametro 'v' non valido: usare side o band |

## Caricamento a pezzi

| Codice | HTTP | Messaggio |
|---|---|---|
| `upload.spazio.insufficiente` | 400 | Spazio disco insufficiente per completare l'upload |
| `upload.chunk.fuori.limiti` | 400 | Chunk fuori dai limiti dichiarati per l'upload |
| `upload.dimensione.non.corrisponde` | 400 | Upload incompleto: dimensione ricevuta non corrisponde a quella dichiarata |
| `upload.non.trovato.scaduto` | 400 | Caricamento non trovato o scaduto: carica di nuovo il file. |
| `upload.sessione.non.trovata` | 404 | Sessione di upload non trovata o scaduta |

## Controllo dei file

| Codice | HTTP | Messaggio |
|---|---|---|
| `file.estensione.non.consentita` | 400 | Tipo di file .{0} non consentito. Estensioni ammesse: {1} |
| `file.estensione.non.consentita.destinazione` | 400 | Tipo di file .{0} non consentito per questa destinazione: sono ammessi solo file di tipo {1}. |
| `file.dimensione.non.valida` | 400 | Dimensione file non valida |
| `file.troppo.grande` | 400 | File troppo grande: {0} pesa {1} MB, oltre il limite di {2} MB per {3}. Riduci le dimensioni del file e riprova. |
| `file.illeggibile` | 400 | File illeggibile |
| `file.contenuto.non.consentito` | 400 | Contenuto del file non consentito: rilevato come {0}, non ammesso per motivi di sicurezza. Carica un file diverso. |
| `file.contenuto.non.corrisponde` | 400 | Il contenuto del file non corrisponde all'estensione .{0} |
| `pdf.javascript` | 400 | Il PDF contiene codice JavaScript: non e' ammesso. Esportalo o stampalo di nuovo come PDF semplice e riprova. |
| `pdf.azione.esterna` | 400 | Il PDF contiene un'azione che avvia programmi o apre file esterni: non e' ammesso. |
| `pdf.illeggibile` | 400 | Il PDF e' illeggibile, danneggiato o protetto da password: non e' possibile verificarne il contenuto. |
| `zip.troppi.file` | 400 | L'archivio ZIP contiene piu' di {0} file: dividilo in archivi piu' piccoli. |
| `zip.dimensione.non.dichiarata` | 400 | L'archivio ZIP non dichiara la dimensione di «{0}»: non e' possibile verificarlo. |
| `zip.troppo.grande` | 400 | L'archivio ZIP una volta estratto supera i 2 GB: dividilo in archivi piu' piccoli. |
| `zip.illeggibile` | 400 | L'archivio ZIP e' illeggibile o danneggiato. |
| `zip.eseguibile` | 400 | L'archivio ZIP contiene «{0}», un file eseguibile o con macro: non e' ammesso. Toglilo dall'archivio e riprova. |
| `zip.archivio.annidato` | 400 | L'archivio ZIP contiene «{0}», che e' a sua volta un archivio compresso: non e' ammesso. Estrailo e caricalo a parte. |

## Archivio documenti

| Codice | HTTP | Messaggio |
|---|---|---|
| `media.file.non.trovato` | 404 | File non trovato |
| `media.file.non.trovato.percorso` | 404 | File non trovato: {0} |
| `media.cartella.non.trovata` | 404 | Cartella non trovata |
| `media.cartella.in.se.stessa` | 400 | Non puoi spostare una cartella dentro se stessa o in una sua sottocartella. |
| `media.cartella.duplicata` | 409 | Esiste gia' una cartella «{0}» in quella posizione. |
| `percorso.non.valido` | 400 | Path non valido |
| `percorso.non.valido.dettaglio` | 400 | Path non valido: {0} |

## Errori generici della richiesta

| Codice | HTTP | Messaggio |
|---|---|---|
| `errore.dati.non.validi` | 400 | Dati non validi |
| `errore.corpo.non.leggibile` | 400 | Corpo della richiesta non leggibile |
| `errore.parametro.non.valido` | 400 | Parametro '{0}' non valido |
| `errore.parametro.obbligatorio` | 400 | Parametro '{0}' obbligatorio |
| `errore.risorsa.non.trovata` | 404 | Risorsa non trovata |
| `errore.vincolo.database` | 409 | Operazione non valida: viola un vincolo del database |
| `errore.file.troppo.grande.limite` | 413 | File troppo grande: supera il limite massimo di {0} MB consentito dal server. Riduci le dimensioni del file e riprova. |
| `errore.file.troppo.grande` | 413 | File troppo grande. Riduci le dimensioni del file e riprova. |
| `errore.interno` | 500 | Errore interno del server |

## Validazione dei campi

Compaiono in `fieldCodes` (e il testo in `fields`) della risposta 400 `errore.dati.non.validi`, un codice per campo.

| Codice | Quando | Messaggio |
|---|---|---|
| `validazione.obbligatorio` | campo vuoto o mancante | Campo obbligatorio |
| `validazione.email.non.valida` | email non ben formata | Indirizzo email non valido |
| `validazione.lunghezza` | testo oltre il massimo ({0}) | Massimo {0} caratteri |
| `validazione.valore.non.valido` | numero fuori limite o altro vincolo | Valore non valido |
| `validazione.password` | password fuori dalla policy | La password deve avere almeno 8 caratteri, con maiuscole, minuscole, numeri e un simbolo, senza spazi |
