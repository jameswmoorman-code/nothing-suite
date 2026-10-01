# Call screener — first live test (server on the Mac)

Everything runs on the Mac for testing: the server, the free speech-to-text,
and ngrok to give Twilio a public address. Nothing is deployed anywhere.
Do the steps in order; each takes a minute or two.

## 0. Before you start

* Twilio number bought (compliance approved). Note it in +44… form.
* Test phone (Phone (3), Vodafone SIM) on the same Wi-Fi as the Mac, or on
  mobile data — either works, it talks to ngrok's public address.
* Android Studio open on the Mac.

## 1. Install the server's bits (once)

Open **Terminal** on the Mac (Cmd+Space, type Terminal) and paste one block
at a time:

```bash
cd ~/Documents/GitHub/nothing-suite/backend-server
npm install
```

```bash
pip3 install -r requirements.txt
```
(This is the free on-Mac speech-to-text, faster-whisper. First run also
downloads a ~150 MB model.) If `pip3` complains about an "externally managed
environment", use `pip3 install --break-system-packages -r requirements.txt`.

```bash
brew install ngrok
```
If `brew` isn't found: install it from brew.sh first (one command on that page).
Then sign up free at ngrok.com, copy the auth token it shows, and run
`ngrok config add-authtoken <paste token>` once.

## 2. Make your `.env` file (once)

```bash
cp .env.example .env
open -e .env
```
TextEdit opens. Fill in:

* `TWILIO_ACCOUNT_SID` and `TWILIO_AUTH_TOKEN` — from the Twilio Console home page.
* `APP_SHARED_SECRET` — any long random string (make one up, 20+ characters).
* `TRANSCRIBER=local` (already set) — free, no OpenAI key needed.
* `PUBLIC_URL` — leave for step 3.
* Optional: change `GREETING_TEXT` to what the assistant should say.

Save and close. `.env` is git-ignored; never commit it.

## 3. Start ngrok, then the server (every test session)

Terminal window 1:
```bash
ngrok http 8080
```
It shows a line like `Forwarding  https://abc123.ngrok-free.app -> http://localhost:8080`.
Copy the https address (it changes each time you restart ngrok on the free plan).

Open `.env` again and set `PUBLIC_URL=https://abc123.ngrok-free.app` (no slash at the end). Save.

Terminal window 2 (Cmd+N for a new window):
```bash
cd ~/Documents/GitHub/nothing-suite/backend-server
npm start
```
You should see `[server] listening on :8080` and the two URLs. Leave both
windows open.

## 4. Point the Twilio number at the server (once per ngrok address)

Twilio Console → Phone Numbers → Manage → Active numbers → click your number →
**Voice Configuration** → "A call comes in": Webhook, URL =
`https://abc123.ngrok-free.app/voice`, HTTP POST. Save.

(Each time the ngrok address changes, update this and `.env`.)

## 5. Build the app onto the phone

Android Studio: top dropdown → **app** → green play. On the phone:

1. Allow the permissions it asks for (phone, contacts, notifications).
2. It asks to become the default phone app — accept. (You can switch back
   any time in Settings → Apps → Default apps → Phone app.)
3. Open its **Setup** screen. Enter:
   * Backend: `wss://abc123.ngrok-free.app/app` (wss, not https)
   * Shared secret: the one from `.env`
   * Twilio number: `+442891244165` (your number)
   Save.
4. Tap **1 · FORWARD IF NO ANSWER** — it dials `*61*…**5#`; wait for the
   network's confirmation. Then **2 · FORWARD IF BUSY** (`*67*…#`), same.
   Both rules are now set on the Vodafone SIM.

## 6. The test

Call the test phone from another phone. On the test phone tap **SCREEN**.
Within a second or two:

* the caller hears the greeting ("Hi, this call is being screened…"),
* Terminal window 2 logs the call arriving and the stream starting,
* the test phone shows the live transcript as the caller speaks.

Try the response buttons. Hang up. Check the saved transcript.

## If it doesn't work

* Caller hears normal ringing then voicemail → forwarding codes didn't take.
  Dial `*#67#` to check busy forwarding; `*#61#` for no-answer.
* Caller hears a Twilio error message → webhook URL wrong or server not
  running. Check Terminal window 2 and the Twilio Console → Monitor → Logs → Calls.
* Transcript stays empty → app's backend URL/secret don't match `.env`, or
  faster-whisper still downloading its model (first run; wait a minute).

## Switching it all off

Phone Setup → **SWITCH FORWARDING OFF** (dials `#61#`; also dial `#67#`).
Settings → Default apps → Phone app → back to Nothing's. Ctrl+C in both
Terminal windows.
