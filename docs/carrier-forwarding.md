# Carrier call forwarding (the bit that makes screening work)

Android gives no API to move a ringing call to another number. What the app
*can* do is go quiet and let the network's own forwarding rules take over.

## How screening actually works

1. Caller rings you. The app shows Answer / Decline / **Screen**.
2. You tap Screen. The app **silences** the call (your phone stops ringing,
   the caller still hears ringing) and opens the transcript screen.
3. Five seconds later your network's "forward when no answer" rule fires
   and diverts the caller to your screener number — exactly the same
   mechanism that normally sends unanswered calls to voicemail.
4. The screener answers, plays a greeting, and streams what the caller says
   to your phone as text.

We chose *no-answer* forwarding over *busy* forwarding deliberately: every UK
network honours the no-answer rule, whereas several treat a declined call
inconsistently. The cost is ~5 s of extra ringing for the caller.

## One-time setup (Settings → the two "Forward" buttons dial these for you)

| Rule | Register | Check | Cancel |
|---|---|---|---|
| Forward when **no answer**, 5 s timer (**required**) | `*61*<ScreenerNumber>**5#` | `*#61#` | `#61#` |
| Forward when **busy** (recommended, covers Decline) | `*67*<ScreenerNumber>#` | `*#67#` | `#67#` |

Use the full international form for the number, e.g. `*61*+4420xxxxxxxx**5#`.
A few networks want `00` instead of `+`. The timer accepts 5, 10, 15 … 30
seconds; 5 is the minimum and the one the app expects.

**Voicemail note:** registering `*61*` replaces your voicemail's no-answer
rule. Unanswered calls you *didn't* screen will also go to the screener, which
answers and transcribes them — arguably better than voicemail. If you'd rather
keep voicemail, cancel with `#61#` when you're not using the app.

## What it costs the user

A forwarded call is billed by your network as if *you* had dialled the
screener number. It's a normal UK number, so on a plan with inclusive minutes
it's free; on pay-as-you-go it's charged as a standard call.

## UK network behaviour (community-reported — please add PRs)

| Network | No-answer forwarding (`*61*`) | Busy forwarding (`*67*`) |
|---|---|---|
| EE | works | works |
| Vodafone | works | works |
| O2 | works | works |
| Three | works | mixed |
| giffgaff / MVNOs | works (host network) | test it |
| Wi-Fi Calling | register while on cellular | register while on cellular |

## Why not "answer then transfer"?

`Call.transfer()` (API 30+) only works on carriers that support explicit
call transfer *and* expose it to the phone. Almost no consumer UK SIM does.
Network forwarding is the universal path and is what every third-party
screening app (RoboKiller, YouMail, Nomorobo) uses.
