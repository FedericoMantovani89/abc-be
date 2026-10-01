#!/usr/bin/env bash
# prepara-server.sh — da lanciare UNA VOLTA SOLA su un server Ubuntu 24.04 appena creato, come root:
#
#     sudo bash script/prepara-server.sh "ssh-ed25519 AAAA... abc-produzione"
#
# L'argomento e' la CHIAVE PUBBLICA SSH del Referente tecnico (il contenuto di id_ed25519.pub).
#
# Cosa fa:
#   1. crea l'utente "deploy" con quella chiave e disattiva l'accesso di root e quello con password
#   2. aggiornamenti di sicurezza automatici, fail2ban, firewall ufw (porte 22, 80, 443)
#   3. installa Docker e Docker Compose
#   4. monta il Volume in /mnt/abc-dati e imposta il fuso Europe/Rome
#   5. limita i registri del server a 14 giorni (contengono indirizzi IP ed email)
#
# Si ferma al primo errore. Rilanciarlo e' sicuro: rifa' solo cio' che manca.
set -Eeuo pipefail

PASSO="avvio"
trap 'echo "ERRORE: prepara-server si e'"'"' fermato al passo: ${PASSO} (riga $LINENO). Nulla di pericoloso e'"'"' stato lasciato a meta: correggi e rilancia." >&2' ERR
passo() { PASSO="$*"; printf '\n== %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "ERRORE: lancia lo script come root:  sudo bash script/prepara-server.sh \"<chiave pubblica>\"" >&2; exit 1; }
CHIAVE_PUBBLICA="${1:-}"
case "$CHIAVE_PUBBLICA" in
  ssh-ed25519\ *|ssh-rsa\ *|ecdsa-sha2-*) ;;
  *) echo "ERRORE: manca la chiave pubblica SSH del Referente (deve iniziare con ssh-ed25519). Vedi INSTALLA.md, passo 2." >&2; exit 1 ;;
esac
grep -q 'Ubuntu' /etc/os-release || { echo "ERRORE: questo script e' scritto per Ubuntu (24.04)." >&2; exit 1; }

UTENTE=deploy
DATI=/mnt/abc-dati

passo "1/7 Utente $UTENTE"
if ! id "$UTENTE" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$UTENTE"
fi
install -d -m 700 -o "$UTENTE" -g "$UTENTE" "/home/$UTENTE/.ssh"
if ! grep -qF "$CHIAVE_PUBBLICA" "/home/$UTENTE/.ssh/authorized_keys" 2>/dev/null; then
  echo "$CHIAVE_PUBBLICA" >> "/home/$UTENTE/.ssh/authorized_keys"
fi
chown "$UTENTE:$UTENTE" "/home/$UTENTE/.ssh/authorized_keys"
chmod 600 "/home/$UTENTE/.ssh/authorized_keys"
# sudo senza password SOLO per il Referente (serve per Docker e per le manutenzioni)
echo "$UTENTE ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/90-abc-deploy
chmod 440 /etc/sudoers.d/90-abc-deploy
visudo -cf /etc/sudoers.d/90-abc-deploy >/dev/null

passo "2/7 Aggiornamenti e programmi di base"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades apt-listchanges unzip

passo "3/7 Fuso orario Europe/Rome"
timedatectl set-timezone Europe/Rome

passo "4/7 Aggiornamenti di sicurezza automatici e fail2ban"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'APT'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
APT
systemctl enable --now unattended-upgrades
systemctl enable --now fail2ban

passo "5/7 Docker e Docker Compose"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
usermod -aG docker "$UTENTE"
docker compose version

passo "6/7 Volume dei dati in $DATI"
if mountpoint -q "$DATI"; then
  echo "il Volume e' gia' montato in $DATI"
else
  # Hetzner collega il Volume come /dev/disk/by-id/scsi-0HC_Volume_<numero>
  VOLUME=$(ls /dev/disk/by-id/scsi-0HC_Volume_* 2>/dev/null | head -n 1 || true)
  if [ -n "$VOLUME" ]; then
    mkdir -p "$DATI"
    if ! grep -qF "$VOLUME" /etc/fstab; then
      echo "$VOLUME $DATI ext4 discard,nofail,defaults 0 0" >> /etc/fstab
    fi
    mount "$DATI"
  else
    echo "ATTENZIONE: non trovo il Volume Hetzner. Se non l'hai collegato, i dati finiranno sul disco del server (80 GB)." >&2
    echo "            Collega il Volume dalla console Hetzner e rilancia questo script, oppure continua se e' una prova." >&2
    mkdir -p "$DATI"
  fi
fi
chown "$UTENTE:$UTENTE" "$DATI"
install -d -m 755 -o "$UTENTE" -g "$UTENTE" /opt/abc-sito

passo "7/7 Registri del server: massimo 14 giorni"
install -d /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/abc.conf <<'JOURNALD'
# I registri contengono indirizzi IP ed email: si tengono al massimo 14 giorni (e al massimo 1 GB).
[Journal]
Storage=persistent
MaxRetentionSec=14day
SystemMaxUse=1G
JOURNALD
systemctl restart systemd-journald

passo "Firewall (ufw): solo 22, 80, 443"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

passo "Accesso SSH: solo con chiave, niente root"
# Il nome 00- fa leggere questo file PRIMA degli altri (in sshd vince la prima impostazione trovata).
cat > /etc/ssh/sshd_config.d/00-abc.conf <<'SSHD'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
SSHD
sshd -t
systemctl reload ssh || systemctl reload sshd

cat <<FINE

== FATTO. NON chiudere questa finestra ancora.
   Apri un SECONDO terminale e prova:   ssh $UTENTE@<IP-del-server>
   - se entri: va bene, puoi chiudere questa finestra e proseguire con il passo 4 di INSTALLA.md
   - se NON entri: da questa finestra (ancora aperta) correggi, per esempio rilanciando lo script
     con la chiave giusta.
   Controlli da fare con l'utente $UTENTE:
     ssh root@<IP>                -> deve essere RIFIUTATO
     docker run --rm hello-world  -> deve funzionare (dopo essere uscito e rientrato)
FINE
