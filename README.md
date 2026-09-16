# ABC Musical Company — Backend

Sito dell'associazione musicale teatrale ABC: area pubblica (spettacoli, eventi, chi siamo),
area soci (calendario prove, comunicazioni, materiale) e pannello admin. Questo repo contiene il
backend (Spring Boot 3.5, Java 21) e la cartella `deploy/` con lo stack Docker completo
(backend + frontend) e gli script di avvio locale.

Il frontend (Next.js) vive nel repo fratello `abc-fe`, nella stessa cartella padre
(`abc-website\abc-fe`).

## Avvio rapido (tutto in Docker)

Prerequisiti: Docker Desktop in esecuzione, repo `abc-fe` clonato come fratello di questo (il
compose in `deploy/` lo builda da `../../abc-fe`).

```sh
# 1. Configura l'ambiente (solo la prima volta) — il .env vive fuori da entrambi i repo
copy deploy\.env.example ..\running\.env
REM poi apri ..\running\.env e controlla password e credenziali

# 2. Avvia tutto
docker compose -f deploy\docker-compose.yml --env-file ..\running\.env up -d

# 3. Apri il sito
#    http://localhost:3000          ← sito
#    admin: ADMIN_EMAIL / ADMIN_PASSWORD definiti in running\.env (creato al primo avvio)
```

Dopo una modifica al codice, aggiungi `--build` al comando di avvio.

## Sviluppo quotidiano (ibrido: Maven locale, senza Docker per il backend)

Prerequisiti: JDK 21, Maven, PostgreSQL condiviso (`nibius-db`, stack `nibius-infra`) gia'
raggiungibile su `localhost:5432` — non c'e' un container Postgres locale gestito da questo repo.

```sh
deploy\backend.bat     # Spring Boot via Maven locale → http://localhost:8080
```

Legge le variabili da `..\running\.env` (vedi `deploy\.env.example`).

## Spostare il sito su un altro PC

1. Copia le tre cartelle `abc-be`, `abc-fe` e `running` (con `running\.env`) sul nuovo PC (con
   Docker installato).
2. Per portare anche i file caricati dagli utenti, esegui il backup sul vecchio PC e il
   ripristino sul nuovo (sotto). Il database e' l'istanza condivisa `nibius-db`: il suo backup e'
   responsabilita' del progetto `nibius-infra`, non di questo repo.
3. `docker compose -f deploy\docker-compose.yml --env-file ..\running\.env up -d` sul nuovo PC.

### Backup e ripristino dei file caricati

```sh
# Backup (crea backup-uploads.tar nella cartella corrente)
docker compose -f deploy\docker-compose.yml stop backend
docker run --rm -v abc-musical_abc_uploads:/data -v "%CD%":/backup alpine tar cf /backup/backup-uploads.tar -C /data .
docker compose -f deploy\docker-compose.yml start backend

# Ripristino (sul nuovo PC, dopo il primo `up -d` e poi lo stop del backend)
docker run --rm -v abc-musical_abc_uploads:/data -v "%CD%":/backup alpine tar xf /backup/backup-uploads.tar -C /data
docker compose -f deploy\docker-compose.yml start backend
```

## Configurazione

Tutto in `..\running\.env` (vedi `deploy\.env.example`, che contiene anche le istruzioni per
Gmail App Password e per i redirect URI OAuth di Google/Facebook).

| Servizio  | URL                          |
|-----------|------------------------------|
| Sito      | http://localhost:3000        |
| API       | http://localhost:8080        |
| Health    | http://localhost:8080/actuator/health |
| Postgres  | localhost:5432 (per DBeaver/psql, credenziali in `running\.env`) — istanza condivisa `nibius-db` |

## Struttura del sito

| Area | URL | Accesso |
|------|-----|---------|
| Pubblica (home, spettacoli, eventi, chi siamo, privacy) | `/`, `/shows`, `/events`, `/about` | libero |
| Auth (login, registrazione, recupero password) | `/login`, `/register`, `/forgot-password` | libero |
| Area soci (dashboard, calendario, comunicazioni, materiale) | `/member/...` | login richiesto |
| Pannello admin (spettacoli, eventi, calendario, comunicazioni, utenti, media, prove) | `/admin/...` | ruolo ADMIN/STAFF/GOD |

L'utente admin iniziale viene creato al primo avvio con `ADMIN_EMAIL` / `ADMIN_PASSWORD` di
`running\.env`.

## Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| `docker compose up` fallisce su una porta occupata | Qualcosa usa gia' 3000, 8080 o 5432: chiudi il processo o cambia il mapping in `deploy\docker-compose.yml` |
| Backend `unhealthy` all'avvio | `docker compose -f deploy\docker-compose.yml logs backend` — quasi sempre e' un valore mancante in `running\.env` (es. `JWT_SECRET`) o `nibius-db` non raggiungibile |
| Modifiche al codice non visibili | Le immagini sono buildate: serve `up -d --build` (vedi sopra) |
| Email di verifica/reset non arrivano | Controlla `SMTP_USER`/`SMTP_PASSWORD` (App Password Gmail, non la password normale) |
| Login Google/Facebook → errore redirect | I redirect URI nelle console OAuth devono puntare a `http://localhost:8080/login/oauth2/code/{provider}` |
| "Troppe richieste" su login/registrazione | Rate limiting (20 req/h per IP su `/api/auth/**`): attendi o riavvia il backend |
| Reset completo dei file caricati | `docker compose -f deploy\docker-compose.yml down -v` (cancella SOLO il volume `abc_uploads`; il database condiviso `nibius-db` non viene toccato) poi `up -d` |

Log in tempo reale: `docker compose -f deploy\docker-compose.yml logs -f backend` (o `frontend`).

## Repo fratello

Frontend Next.js: `..\abc-fe`. Compose Docker e script di avvio condivisi vivono qui, in
`deploy/` — vedi anche `deploy\README.md` per i soli comandi Docker.
