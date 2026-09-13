# Architecture

## Principles

1. **BYOK, no shared servers.** The backend is yours; the keys are yours. The
   project never sees your calls. There is no analytics SDK anywhere.
2. **Keys never leave the device unencrypted.** Twilio/OpenAI keys live only
   in the backend `.env`; the phone stores only the backend URL and the shared
   secret, inside `EncryptedSharedPreferences`.
3. **Separation of concerns.** `:core-design` knows nothing about telephony.
   `telecom/` knows nothing about the network. `glyph/` never touches calls.

## Call-screening sequence

```
Caller rings ──► Android Telecom ──► ScreenerInCallService.onCallAdded(call)
                                              │
                                              ▼
                                  IncomingCallActivity (full-screen)
                                      [Answer] [Decline] [Screen]
                                              │ Screen
                                              ▼
                        ScreenCallAction: call.reject(REJECT_REASON_DECLINED)
                                              │  network sees "busy"
                                              ▼
                        Carrier CFB ──► Twilio number ──► POST /voice
                                              │
                                              ▼
                        LiveTranscriptActivity opens immediately, connects
                        to wss://backend/app?token=…, filters frames where
                        `from` == the number we just rejected.
```

The app opens the transcript screen *before* the forwarded call reaches
Twilio, so the first words are never missed; the first frame is usually
`call_started` 2–4 s later.

## Glyph progress loop

```
Third-party notification ──► GlyphProgressListener (NotificationListenerService)
        │  onNotificationPosted
        ▼
ProgressExtractors.match(packageName, extras)  → ProgressState(percent, label)?
        │
        ▼
GlyphController.showProgress(percent)  → GlyphManager.displayProgress(frame, %)
        │
        └─ onNotificationRemoved → GlyphController.clear()
```

Extractors are pure functions over `Notification.extras` (`EXTRA_PROGRESS`,
`EXTRA_PROGRESS_MAX`, `EXTRA_TITLE`, `EXTRA_TEXT`) plus a per-app phrase map
("Out for delivery" → 80 %, "Arriving now" → 95 %). Add an app by adding a
map entry — no code elsewhere changes.

## Premium lock

`LicenseManager` exposes `StateFlow<Tier>`. Composables and services read
`tier.value.isPremium`. Licence sources are pluggable: the default reads a
signed `license.json` from app-private storage; a Play Billing source can be
dropped in for Play Store distribution.
