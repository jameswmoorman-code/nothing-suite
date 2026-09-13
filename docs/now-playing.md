# Now Playing (ambient music widget)

Pixel phones have "Now Playing"; Nothing phones don't. This app fills the gap
with a dot-matrix widget that names the song playing around you.

## How it works

Every few minutes (5 by default, user-adjustable 2–30) a foreground service
records five seconds from the microphone. If the room is quiet it throws the
clip away without going online. If there's sound, it sends the clip over
HTTPS to the recognition service the user has set up with **their own key**
(ACRCloud by default) and puts the answer on the widget. Nothing is stored;
nothing identifies the user beyond their own API key.

## Things that are different from the original spec, and why

**Foreground service, not WorkManager.** Since Android 11 the microphone is
blocked for apps in the background, and WorkManager *is* background. A
periodic sampler therefore has to be a foreground service of type
"microphone", which means a permanent "Listening for music" notification
and the green mic dot every time it records. There is no quieter option and
users should be told so plainly (the app's first card does this).

**Loudness gate.** Sampling every 5 minutes is 288 requests a day, which
blows through any recognition service's free allowance in a week. The gate
skips silent rooms, and the service also skips while a call is in progress
or headphones are connected (you'd be recognising your own music). Most
users end up at 20–60 real requests a day.

**The widget draws text as pictures.** Home-screen widgets can't use custom
fonts, so `DotMatrixBitmap` renders each line with the design system's
dot-matrix font and the widget shows the image. Same look as the rest of
the suite, no compromise.

## Cost to the user (BYOK)

ACRCloud's free trial covers testing; after that the cheapest plan is a
few pounds a month for a few thousand recognitions. Alternatives with the
same request shape: AudD (has a small free monthly allowance), Audible
Magic. Swap providers by implementing the `Recognizer` interface. There is
no fully-free, on-device option with a big enough song database; that's why
Google can do it and we need a key.

## Google Play notes

Microphone use in the background is a "sensitive permission". Expect to:

* keep the in-app disclosure card (wording is written to Play's template),
* declare the foreground service type and its purpose in the console,
* link a privacy policy that says clips are sent only to the user's own
  recognition account and never stored.

This is a routine review, easier than the dialer's, provided the disclosure
is visible before the mic permission prompt appears — which is how the
setup screen is ordered.

## Lock-screen widget

Android 15+ supports lock-screen widgets only where the OEM enables them.
The widget declares `keyguard` in its category so it appears automatically
when Nothing OS turns that on; nothing more to do on our side.
