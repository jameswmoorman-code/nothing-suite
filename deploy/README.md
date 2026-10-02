# Running the concierge server 24/7 (Oracle Cloud free tier)

The concierge is `backend-server`. Today it runs on your Mac with ngrok; this moves it to an
always-on server so calls are answered with the laptop closed. Oracle's always-free ARM server
is plenty (whisper included) and costs nothing. Hetzner (~£3.30/month) is the fallback if the
Oracle sign-up won't cooperate — the steps from "Create the server" on are identical.

## 1. Account

1. oracle.com/cloud/free → *Start for free*. **Home region: UK South (London)** — cannot be changed later.
2. Normal credit/debit card for the identity check (prepaid/virtual cards get rejected). Nothing is charged.
3. Wait for the "account is ready" email (minutes to hours).

## 2. Create the server

Console → **Compute → Instances → Create instance**

| Setting | Choose |
|---|---|
| Name | concierge |
| Image | Ubuntu 22.04 or 24.04 (**aarch64 / ARM** build) |
| Shape | *Ampere* → **VM.Standard.A1.Flex**, 2 OCPUs, 12 GB (free up to 4/24) |
| Networking | leave defaults; tick *Assign a public IPv4 address* |
| SSH keys | *Generate a key pair for me* → **Save private key** (keep it — it's your login) |

If it says "Out of capacity", try again later or pick 1 OCPU / 6 GB; it varies by the hour.

Then open the ports: **Networking → Virtual cloud networks → your VCN → Security Lists → Default →
Add Ingress Rules** twice: source `0.0.0.0/0`, protocol TCP, destination port `80`; and again for `443`.

## 3. Install (one command)

On your Mac, Terminal:

```
chmod 600 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@<the server's public IP>
```

On the server:

```
curl -fsSL https://raw.githubusercontent.com/jameswmoorman-code/nothing-suite/main/deploy/oracle-setup.sh | bash
```

Ten minutes later it prints your address, like `https://1-2-3-4.sslip.io` (a free name that points at
the server's IP; HTTPS certificate is automatic).

## 4. Your keys

```
nano ~/nothing-suite/backend-server/.env
```

Copy the values from the `.env` on your Mac (`~/Documents/GitHub/nothing-suite/backend-server/.env`):
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, APP_SHARED_SECRET, TWILIO_NUMBER, USER_NUMBER, GREETING_TEXT.
Leave PUBLIC_URL and PYTHON_BIN as the script set them. Ctrl+O, Enter, Ctrl+X, then:

```
sudo systemctl restart concierge
journalctl -u concierge -f      # watch it start; Ctrl+C to stop watching
```

## 5. Point things at it

* Twilio → Phone Numbers → your number → *A call comes in* → `https://<address>/voice` → Save.
* Nothing app → Setup → Backend → `wss://<address>/app` → Done.

Ring the number. Done — ngrok and the Mac window are no longer needed.

## Later

* After a GitHub push: `ssh` in, then `bash ~/nothing-suite/deploy/update.sh`.
* Logs: `journalctl -u concierge -f`. Status: `systemctl status concierge caddy`.
* Reboots are fine: both services start automatically.
