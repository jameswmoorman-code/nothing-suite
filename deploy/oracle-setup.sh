#!/usr/bin/env bash
# One-shot setup for the concierge server on a fresh Ubuntu 22.04/24.04 box
# (Oracle Cloud always-free ARM, Hetzner, Raspberry Pi — anything Ubuntu).
#
#   curl -fsSL https://raw.githubusercontent.com/jameswmoorman-code/nothing-suite/main/deploy/oracle-setup.sh | bash
#
# What it does:
#   1. installs Node 20, Python 3 + faster-whisper, Caddy (automatic HTTPS)
#   2. clones the repo to ~/nothing-suite and installs the server
#   3. gives the server a free HTTPS address  https://<ip>.sslip.io  (no domain to buy)
#   4. runs it as a service that survives reboots
# Afterwards you paste your .env (secrets) once — see deploy/README.md.
set -euo pipefail

REPO="https://github.com/jameswmoorman-code/nothing-suite.git"
APP_DIR="$HOME/nothing-suite"
PUBLIC_IP="$(curl -4fsSL https://ifconfig.me || curl -4fsSL https://api.ipify.org)"
HOST="${PUBLIC_IP//./-}.sslip.io"

echo "==> Public address will be: https://$HOST"

echo "==> Packages"
sudo apt-get update -qq
sudo apt-get install -y -qq git curl python3 python3-pip python3-venv ffmpeg debian-keyring debian-archive-keyring apt-transport-https ca-certificates gnupg

echo "==> Node 20"
if ! command -v node >/dev/null || [[ "$(node -v)" != v2* ]]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y -qq nodejs
fi

echo "==> Caddy (automatic HTTPS)"
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
  sudo apt-get update -qq && sudo apt-get install -y -qq caddy
fi

echo "==> Code"
if [[ -d "$APP_DIR/.git" ]]; then git -C "$APP_DIR" pull --ff-only; else git clone --depth 1 "$REPO" "$APP_DIR"; fi
cd "$APP_DIR/backend-server"
npm ci --omit=dev 2>/dev/null || npm install --omit=dev

echo "==> Whisper (free, local transcription)"
python3 -m venv "$APP_DIR/.venv"
"$APP_DIR/.venv/bin/pip" install -q --upgrade pip
"$APP_DIR/.venv/bin/pip" install -q -r requirements.txt
# pre-download the model so the first call isn't slow
"$APP_DIR/.venv/bin/python" - <<'PY' || true
from faster_whisper import WhisperModel
WhisperModel("base.en", device="cpu", compute_type="int8")
print("whisper model ready")
PY

echo "==> .env"
if [[ ! -f .env ]]; then
  cp .env.example .env
  sed -i "s|^PUBLIC_URL=.*|PUBLIC_URL=https://$HOST|" .env
  sed -i "s|^PYTHON_BIN=.*|PYTHON_BIN=$APP_DIR/.venv/bin/python|" .env
  echo "    created backend-server/.env — you still need to paste your Twilio keys etc. into it"
fi

echo "==> Firewall (Oracle blocks everything by default at the OS level too)"
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || true
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || true
sudo netfilter-persistent save 2>/dev/null || sudo sh -c 'iptables-save > /etc/iptables/rules.v4' 2>/dev/null || true

echo "==> Caddy config"
sudo tee /etc/caddy/Caddyfile >/dev/null <<CADDY
$HOST {
    reverse_proxy 127.0.0.1:8080
}
CADDY
sudo systemctl enable --now caddy
sudo systemctl reload caddy

echo "==> Service"
sudo tee /etc/systemd/system/concierge.service >/dev/null <<UNIT
[Unit]
Description=Nothing Suite concierge (call screener server)
After=network-online.target
Wants=network-online.target

[Service]
User=$USER
WorkingDirectory=$APP_DIR/backend-server
ExecStart=$(command -v node) src/index.js
Restart=always
RestartSec=3
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now concierge

echo
echo "================================================================"
echo " Done. Your server address is:   https://$HOST"
echo
echo " Next:"
echo "   1. nano ~/nothing-suite/backend-server/.env   (paste your keys, Ctrl+O, Enter, Ctrl+X)"
echo "   2. sudo systemctl restart concierge"
echo "   3. Twilio → your number → Voice webhook:   https://$HOST/voice"
echo "   4. App → Setup → Backend:                  wss://$HOST/app"
echo " Logs:  journalctl -u concierge -f"
echo " Update after a GitHub push:  cd ~/nothing-suite && git pull && sudo systemctl restart concierge"
echo "================================================================"
