# Selling on Google Play, to Nothing phones only

## Costs and cuts, up front

* Google Play developer account: one-off registration fee (about £20 at the
  time of writing; check the current figure when you sign up).
* Google keeps 15 % of every in-app sale up to $1 M/year. You receive the rest,
  minus VAT, which Google collects and remits for you.
* Nothing else. No hosting, no payment provider, no VAT registration needed.

## Restricting the listing to Nothing phones

Play Console → your app → **Reach and devices → Device catalogue**.

1. Open the **Excluded devices** tab.
2. Filter by *Manufacturer* → select everything **except** Nothing → exclude.
3. Save. Only Nothing devices can now find or install the app.

The app also checks `Build.MANUFACTURER == "Nothing"` at runtime and disables
Glyph features on anything else, so a sideloaded copy on another phone gets
the screener but not the light show.

## Creating the two products

Play Console → **Monetise → Products → In-app products → Create product**.

| Product ID | Name | Price |
|---|---|---|
| `nothing_suite_plus` | Plus — Glyph visualisers | £2.99 |
| `nothing_suite_pro` | Pro — everything, forever | £4.99 |

Both are **one-time** (non-consumable). The IDs must match the constants in
`PlayBillingLicenseSource.kt` exactly. Activate them once the first build is
uploaded to any track (products can't be tested before that).

## Testing purchases without paying

Play Console → **Setup → Licence testing** → add your own Google account.
Install from the *Internal testing* track. Purchases show as "Test card,
always approves" and cost nothing. Use **Restore purchase** in the app to
pull them back after a reinstall.

## The permissions review — expect questions

Because this app asks to be the **default phone app** and to **read
notifications**, Google's policy team will ask you to fill in a
*Permissions declaration form* when you first submit. What they want:

* Default handler — select **Default Phone app**; describe the incoming-call
  screen and the screening feature. Core use case: "Call screening".
* Notification listener — explain the Glyph progress tracker reads *status*
  notifications (delivery/transit) to drive the rear LEDs, and that nothing
  is stored or transmitted.
* A short screen recording (30–60 s) showing the permission prompts and the
  features they enable.
* A privacy policy URL. A single page stating "no data is collected; all
  processing happens on the user's own device and server" is enough — host
  it on any site you already run.

First review typically takes a few days to two weeks and one round of
clarification is normal. Answer plainly and it goes through.

## Data safety form

Answer **No** to data collection and **No** to data sharing. Phone numbers
and notification text are processed in memory only and never leave the
device except to the server *the user themselves* runs.
