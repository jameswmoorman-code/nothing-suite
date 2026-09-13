# Shade Guard (anti-theft Quick Settings lock)

**Status: experimental. Read this before spending time on it.**

## What it does

When the phone is locked and someone pulls down the notification shade, the
service closes it instantly and asks for a fingerprint. Pass the check and
the shade works normally for 45 seconds. Fail it and the shade stays shut.
The point is to stop a snatch-thief flicking on flight mode or switching
off mobile data so the phone can't be located or remotely locked.

## Why it isn't built the way it was first described

The original spec asked for an invisible overlay window over the status-bar
pull-down zone. That can't work for a normal app on modern Android:

* Overlay windows are drawn *underneath* the status bar, not over it.
* Overlays are hidden entirely while the lock screen is showing.
* Since Android 12 an overlay cannot intercept a touch and pass it on.

The only signal a third-party app can receive is the accessibility event
System UI emits when the shade opens, and the only action it can take is
"dismiss the notification shade". So Shade Guard is an accessibility
service. That is both its mechanism and its main problem (see below).

## Known limits — be honest with users

1. **There is a gap.** Between the shade opening and our dismiss action
   there are roughly 50–150 ms. A thief who taps the flight-mode tile in
   that gap wins. In practice people don't, but it isn't airtight.
2. **Power-off isn't covered.** Long-pressing power still offers "Power
   off" on the lock screen. Nothing OS may add a "require unlock to power
   off" option in a future release; nothing an app can do about it today.
3. **Android 15/16 already do a lot of this.** Google's Theft Detection
   Lock, Offline Device Lock, Remote Lock and (on newer builds) the
   requirement to authenticate before changing sensitive settings ship in
   the OS on Nothing Phone (2) and later. Shade Guard closes one remaining
   hole — the lock-screen Quick Settings tiles — not the whole problem.
   Check what the user's Nothing OS version already offers before pitching it.
4. **Google Play and accessibility services.** Play only allows
   accessibility services for genuine accessibility purposes or a short list
   of exceptions, and requires a declaration form plus a prominent in-app
   disclosure. Security tools are sometimes accepted, sometimes not, and
   the decision can flip on appeal. Plan for the possibility that this one
   ends up as a GitHub-only download rather than a Play listing.
5. **Detection is heuristic.** We match System UI window class names.
   Nothing OS is close to stock so the stock names hold, but a big Nothing
   OS update could rename them and silently break the guard until updated.

## Testing it

1. Install, open the app, tap "Open accessibility settings", switch Shade
   Guard on.
2. Lock the phone. Pull down from the top.
3. Expected: the shade snaps shut, the fingerprint prompt appears. Scan a
   finger; pull down again — it opens normally. Wait 45 s; it's gated again.
4. The app's "Last event" card shows what the service saw, which is the
   first thing to look at if step 3 doesn't happen.
