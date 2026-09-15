# Dot Widgets (roadmap #1)

Dot-matrix home-screen widgets for **any** Android 8+ phone. No KWGT, no
permissions, no servers. Free: Clock, Date, Battery. £2.99 one-time unlock:
Progress, Countdown, Label.

## Why it's built the way it is

* **Plain RemoteViews, not Glance.** The dot-matrix font is applied in the
  layout XML (`android:fontFamily="@font/doto_black"`), which launchers
  honour, so there's no bitmap rendering and `TextClock` ticks for free.
* **Battery refreshes on plug/unplug** via manifest broadcasts, plus a
  30-minute periodic update. No background service.
* **Locked widgets still render** — as a black card saying UNLOCK IN APP —
  so people can see the shape before paying; tapping opens the app.
* **`DotWidget` base class** owns the paywall check, tap-to-open and refresh
  plumbing. A new widget is a layout file plus ~15 lines of Kotlin.

## Play listing copy (draft)

**Title:** Dot Widgets — dot-matrix clock & more
**Short description:** Monochrome dot-matrix widgets. Clock, date, battery,
progress, countdown. No KWGT needed.
**Full description:**
The dot-matrix look, on any phone. Six clean black-and-white widgets that
work straight from your widget picker — no KWGT, no setup, no permissions.
Clock, Date and Battery are free. Unlock Progress, Countdown and Label once
for £2.99; no subscriptions, ever.

Nothing is collected. The app has no internet permission.

**Category:** Personalisation. **Tags:** widgets, dot matrix, monochrome, minimal.

## Product ID

`dot_widgets_all` · one-time · £2.99 (create in Play Console → Monetise → In-app products).

## Store screenshots

`docs/images/dot-widgets-*.png` — rendered from the same styles as the
layouts. Replace with device screenshots once it's running.
