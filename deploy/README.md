# deploy/

Compose e script di avvio locale per ABC Musical. Il backend (questo repo, `abc-be`) e il
frontend (`abc-fe`) sono due repository fratelli sotto la stessa cartella `abc-website\`; questa
cartella si aspetta la struttura:

```
abc-website/
  abc-be/     <- questo repo
    deploy/
  abc-fe/
  running/
    .env      <- segreti reali, non versionato
```

## Avvio con Docker

```sh
cp deploy\.env.example ..\running\.env   # solo la prima volta, poi compila i valori reali
docker compose -f deploy\docker-compose.yml --env-file ..\running\.env up -d
docker compose -f deploy\docker-compose.yml --env-file ..\running\.env up -d --build   # dopo modifiche al codice
```

Richiede due network Docker **esterne** già create (`edge`, `nibius-data`) e un Postgres esterno
(`nibius-db`, stack `nibius-infra`) — non vengono creati da questo compose.

## Avvio ibrido (senza Docker per le app)

Prerequisiti: JDK 21 + Maven per il backend, Node 22+ per il frontend, `running\.env` compilato
(vedi sopra).

```sh
deploy\backend.bat    # Spring Boot via Maven -> http://localhost:8080
deploy\frontend.bat   # Next.js via npm run dev -> http://localhost:3000
```

`deploy\frontend.bat` rigenera `abc-fe\.env.local` a partire da `running\.env` ad ogni avvio —
non modificarlo a mano.
