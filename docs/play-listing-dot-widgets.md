# Dot Widgets — Play Store listing pack

Everything to paste into Play Console, in the order the Console asks for it.
Copy each block as-is. Character limits are Google's.

---

## 1. App name (30 chars max)

```
Dot Widgets
```

## 2. Short description (80 chars max)

```
Dot-matrix widgets for any Android. Clock, weather, steps, streaks. No KWGT.
```

## 3. Full description (4000 chars max)

```
The dot-matrix look, on any Android phone.

Twelve clean black-and-white widgets that work straight from your widget
picker. No KWGT, no setup, no accounts. Long-press your home screen, open
Widgets, search "dot".

FREE
• Clock — big dot-matrix time with the date underneath
• Date — day and date, nothing else
• Battery — level as a row of dots, refreshes when you plug in

UNLOCK ALL — one payment, no subscription, ever
• Streak — days since you stopped (or started). Quit smoking, stopped
  drinking, first run. Pick an icon, add a note, get a milestone on day
  7, 30, 100 and every year
• Countdown — days to a date, or tap WEEKEND, PAYDAY, XMAS, NEW YEAR and
  it repeats by itself
• Steps — today's steps against your goal, counted by the phone itself
• Weather — temperature and sky for your town, from a free service. No
  location permission
• Next Up — your next calendar event and how long until it
• Progress — how far through the day, month and year you are
• Quote — one short line a day, ours or your own list
• Label — any text you like, in dots
• Storage — how much space is left

STYLE
Every widget comes as a rounded card, a circle, or bare text straight on
your wallpaper. Set it once for all, or per widget.

PRIVACY
Nothing is collected and nothing leaves your phone except the weather
request for the town you typed. No ads, no analytics, no account.

Works on any phone running Android 8 or later. Designed to sit alongside
the Nothing OS look; not affiliated with Nothing Technology.
```

## 4. Category & tags

* App category: **Personalisation**
* Tags: widgets, dot matrix, monochrome, minimal, clock widget, streak, habit

## 5. Contact details

* Email: apps@ukcalc.uk
* Website: https://ukcalc.uk
* Privacy policy URL: https://ukcalc.uk/dot-widgets/privacy (page text in §9)

## 6. Store settings questionnaire — the answers

**App access:** All functionality is available without special access.

**Ads:** No, my app does not contain ads.

**Content rating (IARC questionnaire):** Utility/Productivity. Answer "No" to
everything (violence, sexual content, language, controlled substances,
gambling, user interaction, sharing location, purchases of digital goods
— YES to that last one: "Does the app allow users to purchase digital
goods?" → Yes). Expected rating: PEGI 3 / Everyone.

**Target audience:** 18 and over. (Simplest; avoids the families policy.)

**News app:** No.

**COVID-19 contact tracing:** No.

**Data safety:**
* Does your app collect or share any of the required user data types? **No.**
* Is all of the user data collected by your app encrypted in transit? (only asked if Yes above — skip)
* Note for the reviewer, if there's a free-text box: "The Weather widget
  sends the coordinates of a town the user typed to api.open-meteo.com to
  fetch a forecast. No identifiers are sent. Steps and Calendar are read
  on-device only and never transmitted."

**Government apps:** No.

**Financial features:** None.

**Health:** Select "My app does not have any health features" — the step
counter is a convenience display, not a health service. (If the Console
insists because of ACTIVITY_RECOGNITION, pick "Fitness and exercise" →
step counting, and state that data stays on device.)

## 7. Permissions declaration (only if the Console prompts)

| Permission | Why (paste this) |
|---|---|
| ACTIVITY_RECOGNITION | Dot Steps widget reads the phone's step counter to show today's steps. Requested only when the user adds that widget. Data never leaves the device. |
| READ_CALENDAR | Dot Next Up widget shows the title and time of the user's next event. Requested only when the user adds that widget. Read-only, on device. |
| INTERNET | Dot Weather widget fetches a forecast from Open-Meteo for a town the user typed. |
| SCHEDULE_EXACT_ALARM | Dot Clock redraws on the minute. Optional; falls back to inexact alarms. |
| VIBRATE | Haptic tick on buttons. |

## 8. In-app product

Play Console → Monetise → Products → In-app products → Create product

* Product ID: `dot_widgets_all` (must match exactly)
* Name: Unlock all widgets
* Description: All twelve Dot Widgets, forever. One payment, no subscription.
* Price: £2.99 (let Play convert other currencies)
* Status: Active

## 9. Privacy policy page (put at ukcalc.uk/dot-widgets/privacy)

```
Dot Widgets — Privacy Policy
Last updated: 20 September 2026

Dot Widgets is made by James Moorman (apps@ukcalc.uk).

What we collect: nothing. Dot Widgets has no account, no analytics, no
advertising and no crash reporting. We do not know who you are or how you
use the app.

What stays on your phone: your widget settings (labels, dates, goals,
quotes, town name) are stored in the app's private storage on your phone
only. Step counts and calendar events are read from your phone's own
sensors and calendar to draw the widget, and are never sent anywhere.

The one network request: if you add the Weather widget and type a town,
the app looks up that town's coordinates and then asks api.open-meteo.com
for the forecast. Open-Meteo receives the coordinates and your phone's IP
address, as any web request does. No identifier from you or your phone is
sent. Open-Meteo's own policy is at open-meteo.com/en/terms.

Purchases: the "Unlock all" purchase is handled entirely by Google Play.
We receive no payment details.

Children: the app is not directed at children under 13.

Changes: if this policy changes, the new version will appear at this
address with a new date.

Contact: apps@ukcalc.uk
```

## 10. Screenshots (phone, 1080×2340 or similar; 2–8 required)

Use the four device screenshots from 20 Sep, in this order:
1. Battery + Streak + Weather (the hero shot)
2. Steps + Quote + Storage
3. The app's gallery screen
4. (after the polish build) a circle-style home screen

Feature graphic (1024×500, required): black background, the twelve preview
cards tiled small, "DOT WIDGETS" in dot-matrix across the middle. I'll
render this.

App icon (512×512): the current launcher icon exported at size.

## 11. Closed testing — the steps

1. Play Console → Testing → Closed testing → Create track → name it "beta".
2. Testers: add an email list; paste the tester emails (need 12+ who opt in
   and stay 14 days). Source: the Nothing Community thread + Reddit + friends.
3. Upload the signed .aab (Android Studio → Build → Generate Signed App
   Bundle — I'll walk you through the signing key when we get there).
4. Release notes for the first build:
   ```
   First closed test. Twelve dot-matrix widgets. Please try adding each one
   and tell us anything that looks wrong at apps@ukcalc.uk.
   ```
5. Save → Review release → Start rollout to Closed testing.
6. Copy the opt-in link the Console gives you and post it to testers.
7. After 14 days with 12+ testers, apply for production access
   (Dashboard → "Apply for production").

## 12. Tester recruitment post (Nothing Community / Reddit)

```
Looking for 12 testers: Dot Widgets — dot-matrix home screen widgets

I've built a set of twelve monochrome dot-matrix widgets (clock, weather,
steps, a quit-smoking streak counter, countdowns to payday/weekend, and
more). Works on any Android, made to match the Nothing look. A Glyph
Matrix pack for Phone (3) is coming next.

Google needs 12 testers for two weeks before it'll let a new developer
publish. If you'd like early access (all widgets unlocked during the test),
reply or DM me your Google account email and I'll add you. Opt-in link
follows once you're on the list.

Screenshots below. Open source: github.com/jameswmoorman-code/nothing-suite
```
