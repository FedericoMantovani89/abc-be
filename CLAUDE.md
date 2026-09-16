# CLAUDE.md

Questo file fornisce indicazioni a Claude Code (claude.ai/code) quando lavora con il codice di questo repository.

## Panoramica del progetto

Backend di ABC Musical Company (associazione musicale teatrale), sito in italiano. Spring Boot
3.5 / Java 21, Maven. Tre aree servite: sito pubblico (spettacoli, eventi, chi siamo), area soci
(`/api/member/...`, login richiesto) e pannello admin (`/api/admin/...`, ruoli ADMIN/STAFF/GOD).

Questo repo (`abc-be`) e' uno dei due nati dallo split del vecchio monorepo `abc-site`:

- `abc-be` (questo repo) — backend Spring Boot. Possiede anche `deploy/` (docker-compose, script
  di avvio, `.env.example`), perche' i due repo condividono lo stesso stack Docker.
- `abc-fe` — frontend Next.js, repo fratello nella stessa cartella padre (`abc-website/`).

## Struttura del repo

- `pom.xml`, `src/main/java/it/abc/musical/...`, `src/test/...` — sorgenti Spring Boot, alla
  radice del repo (non piu' sotto un prefisso `backend/` come nel vecchio monorepo).
- `Dockerfile` — build dell'immagine backend.
- `deploy/` — `docker-compose.yml`, `backend.bat`/`frontend.bat`, `.env.example`,
  `deploy-frontend.ps1`. Il compose builda `backend` dalla radice di questo repo (`..`) e
  `frontend` dal repo fratello (`../../abc-fe`).

## Comandi comuni

### Backend

```sh
mvn spring-boot:run                                    # avvio (profilo da SPRING_PROFILES_ACTIVE)
mvn test                                                # suite di test completa
mvn test -Dtest=JwtTokenServiceTest                     # singola classe di test
mvn test -Dtest=JwtTokenServiceTest#nomeMetodo          # singolo metodo di test
mvn -DskipTests package                                  # build del jar senza eseguire i test
```

I test di integrazione (es. `ContextLoadTest`, `AdminApiTest`, `AuthFlowTest`) usano Testcontainers
e avviano un vero container `postgres:16-alpine` — Docker deve essere avviato. Il profilo Spring
`test` legge `src/test/resources/application-test.yml`.

### Sviluppo locale ibrido (Maven diretto sull'host)

Prerequisiti: JDK 21, Maven, PostgreSQL condiviso (`nibius-db`, stack `nibius-infra`) avviato e
raggiungibile su `localhost:5432`, file `abc-website\running\.env` compilato (copia da
`deploy\.env.example`).

```sh
deploy\backend.bat     # Spring Boot via Maven → http://localhost:8080
```

Non c'e' un container Postgres locale gestito da questo repo: il database e' l'istanza condivisa
`nibius-db` di un progetto infrastrutturale separato (`nibius-infra`) — non ricrearla ne' fermarla
da qui.

### Stack completo in Docker

Dalla cartella `deploy/` (dettagli completi in `deploy/README.md`):

```sh
docker compose --env-file ..\..\running\.env up -d
docker compose --env-file ..\..\running\.env up -d --build   # dopo modifiche al codice
docker compose logs -f backend
```

Richiede due network Docker **esterni** (`edge`, `nibius-data`) gia' esistenti — non crearle ne'
ricrearle da qui: sono condivise con altri progetti sulla stessa macchina.

## Architettura

### Autenticazione: JWT emesso da Spring

L'emissione/parsing del JWT vive in `security/` (`JwtTokenService`, `JwtAuthoritiesConverter`,
`CustomOAuth2UserService`/`CustomOidcUserService` per il flusso di login OAuth2,
`OAuth2AuthenticationSuccessHandler` per il redirect verso il frontend con il token).
`RateLimitingFilter` (Bucket4j) limita `/api/auth/**` a 20 richieste/ora per IP.

Il frontend (`abc-fe`) decodifica questo stesso JWT lato NextAuth (`userFromToken()` in
`auth.ts`) e lo ricontrolla ad ogni richiesta — dettagli in `abc-fe/CLAUDE.md`. Gli endpoint
chiamati sono suddivisi per area: `controllers/api` (`Admin*Controller`, `Member*Controller`,
`AccountController`) e le pagine pubbliche direttamente sotto `controllers/` (`Public*Controller`).
Alcuni endpoint di auth (`/api/auth/register`, `/forgot-password`, `/reset-password`, `/verify`,
`/resend-verification`) vengono raggiunti dal frontend tramite un rewrite Next dedicato — vedi
`abc-fe/CLAUDE.md`.

### Struttura del codice (`src/main/java/it/abc/musical/`)

Struttura a livelli standard di Spring Boot: `controllers/api`, `services`, `repositories`
(Spring Data JPA), `entities`, `dto` (raggruppati come `*Dtos` per dominio, non un file per DTO),
`config`, `security`, `validation`, `util`.

- Lo schema e' gestito da Flyway (`src/main/resources/db/migration/V00N__*.sql`); Hibernate e' in
  `ddl-auto: validate` — non affidarsi mai a Hibernate per creare/alterare lo schema, aggiungere
  una migration. `AdminSeeder` crea l'utente admin iniziale e i ruoli al primo avvio a partire da
  `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- `FileValidationService` + Apache Tika + `HtmlSanitizer` (jsoup) proteggono upload/contenuti HTML
  inseriti dagli utenti — continuare a usare questi strumenti invece di fidarsi del MIME type
  fornito dal client.
- I ruoli sono righe seedate (7 in totale, verificate in `ContextLoadTest`), non un enum —
  controllare `RoleRepository`/entita' `Role` prima di assumere un set di ruoli fisso.

### Configurazione

Le variabili d'ambiente vivono in `abc-website\running\.env` (cartella fratello di questo repo,
NON versionata — vedi `deploy\.env.example` per l'elenco completo e i placeholder). Sia il
compose in `deploy/` sia `deploy\backend.bat` leggono da li'. `SPRING_PROFILES_ACTIVE` seleziona
`application-dev.yml` / `application-docker.yml` sopra la base `application.yml`.

## Repo fratello

Il frontend vive in `..\abc-fe` (repo separato, remote proprio). Le istruzioni specifiche del
frontend (auth NextAuth, chiamate API, regole di stile) sono nel suo `CLAUDE.md`.
