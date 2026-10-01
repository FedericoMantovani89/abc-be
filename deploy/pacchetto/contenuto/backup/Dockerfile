# Immagine del servizio "backup": copia ogni notte database e file caricati, CIFRATI, sulla Storage Box.
# Il contesto di build e' la cartella "contenuto" del pacchetto (lo fa crea-pacchetto.ps1).
FROM alpine:3.20
RUN apk add --no-cache bash restic postgresql16-client openssh-client tzdata
COPY backup/comune.sh backup/backup.sh backup/ciclo.sh backup/ripristina.sh /backup/
COPY script/registro-cancellazioni.sh /backup/registro-cancellazioni.sh
RUN chmod 0755 /backup/*.sh
ENTRYPOINT ["/backup/ciclo.sh"]
