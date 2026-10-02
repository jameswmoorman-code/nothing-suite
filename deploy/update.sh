#!/usr/bin/env bash
# Pull the latest code from GitHub and restart the concierge. Run on the server.
set -euo pipefail
cd "$HOME/nothing-suite"
git pull --ff-only
cd backend-server && (npm ci --omit=dev 2>/dev/null || npm install --omit=dev)
sudo systemctl restart concierge
echo "updated — logs: journalctl -u concierge -f"
