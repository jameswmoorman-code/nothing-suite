# Portfolio roadmap — 12 months, 11 apps

One design system, one Play account, one build setup. Each app does one
thing well, ships small, and cross-promotes the others. Same logic as a
portfolio of utility websites: no single app is a living, the set is.

Reviewed: 15 September 2026.

## Principles

1. **One thing, done reliably.** The crowded Glyph apps sit at 3.8★ because
   one feature breaks on one model. A 4.5★ app that does less outsells them.
2. **No servers, no risky permissions** for the money-makers. Servers cost;
   permissions cost weeks in Play review. The call screener is the only
   exception and it is a portfolio piece, not an earner.
3. **"This look on any phone."** Nothing's own free packs have 500K+
   installs — mostly non-Nothing owners. Everything customisation-related
   targets all of Android; only the Glyph apps are Nothing-only.
4. **Never put "Nothing" in an app name.** It's their trademark. "Dot",
   "Dots", "Glyph…" are the community convention.
5. **Free + one-time unlock** everywhere. Nobody pays monthly for a widget.

## The eleven

| # | App | Module | Audience | Price | Effort | Ships |
|---|---|---|---|---|---|---|
| 1 | **Dot Widgets** — clock, date, battery, progress, countdown, label; no KWGT needed | `dot-widgets` | Any Android | Free 3 / £2.99 all | 2–4 wk | Oct |
| 2 | **Dots Icons** — auto-generated dot-matrix icon pack, 5–10K icons + wallpapers | `dots-icons` | Any Android | Free / £2.49 | 2–3 wk | Nov |
| 3 | **Glyph Tracker** — deliveries, transit, rides on the Glyph strip / matrix | `glyph-tracker` | Nothing (2)+ | Free 3 apps / £1.99 | 2 wk (mostly built) | Nov |
| 4 | **Dot Wallpapers** — live wallpaper with dot clock + battery ring; static packs | `dot-wallpapers` | Any Android | Free / £1.99 | 2 wk | Dec |
| 5 | **Dot AOD** — always-on display, charging screen, screensaver | `dot-aod` | Any Android | £1.99 | 2–3 wk | Jan |
| 6 | **Glyph Toybox Pro** — useful matrix toys: level, dice, Pomodoro, dB meter, tally | `glyph-toybox` | Phone (3)/(4) | £1.49 | 2 wk | Jan |
| 7 | **Dotify** — photo → dot-matrix art, share cards | `dotify` | Any Android | Free / £1.99 | 2 wk | Feb |
| 8 | **Pixels** — habit/streak tracker, each day a dot, with widgets | `pixels` | Any Android | Free / £2.99 | 3 wk | Mar |
| 9 | **UKCalc** — take-home pay, mortgage, stamp duty, with widgets | `ukcalc` | UK public | Free + ads / £1.99 | 3 wk | Apr |
| 10 | **Inspect** — landlord mid-term inspection & inventory, PDF report | `inspect` | UK landlords | £12.99 | 5–6 wk | May–Jun |
| 11 | **Dot Launcher** — text-first minimalist launcher | `dot-launcher` | Any Android | Free / £3.99 | 6–8 wk | Jul–Aug |

Already in the repo, kept as reputation rather than income: the AI call
screener (`app`), Shade Guard (`anti-theft-module`), Now Playing
(`music-tracker-module`).

## Why this order

* **1 → 2 → 4** share assets and the same buyers; each listing links the others.
* **3 and 6** ship while the Nothing Community thread is warm.
* **7** is the marketing app: people post the results.
* **9 and 10** use traffic James already has (ukcalc.uk, tenancytools.uk).
* **11** is the biggest bet and the biggest build; last, once the store is learnt.

## Money, honestly

Comparable apps: a decent small one reaches 10–30K installs over two years
and nets £1–5K over its life; a good one several times that; some flop.

| Year | Base case | Good case (one breakout) |
|---|---|---|
| 1 | £2–6K | £8–15K |
| 2 (mature) | £5–15K | £20–30K |

Costs: £20 Play account (once), ~£1/month for the screener's number,
nothing else. Google keeps 15 %.

## Shared plumbing (build once)

* `core-design` — theme, dot-matrix font (Doto, OFL, bundled), components.
* `core-billing` — Play Billing one-time unlock, product IDs per app.
* Store-listing kit — screenshot renderer, feature graphic template, privacy page.
* One GitHub Actions workflow that builds every module on push.

## Play Store notes

* The 12-tester / 14-day closed test applies to the account's **first**
  production app only. Lead with Dot Widgets — no permissions, trivial review.
* Device catalogue restriction to Nothing phones for the Glyph apps only.
* Every listing: same privacy page ("no data collected"), same support email.

## Maintenance

Eleven apps = eleven things to touch when Android changes each autumn.
Keeping them on one design library and one Gradle setup means a fix lands
in all of them from one commit. Budget one week a year for it.
