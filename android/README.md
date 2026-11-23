# Panadoro 1.9.5

Native Kotlin/Jetpack Compose build of the merged Panadoro habit tracker and
Tomato focus timer. The UI data model and Firebase wire format are compatible
with the quickshell app, so both clients stay in sync.

## What's new in 1.9.5

- Focus stats are midnight-accurate. A session that runs past midnight is
  split at every local midnight it crosses, and each day is credited with the
  part that actually happened on it: 23:50→00:20 now gives ten minutes to the
  first day and twenty to the next — in today's focus totals and session
  count, the streak, the rhythm chart, the heatmap, the time-of-day buckets
  and the Ranks day/week/month leaderboard totals.
- A paused session spreads its recorded focus seconds over its wall-clock span
  in proportion to each part's length, so the parts always add back up to
  exactly the session's recorded duration — paused time is never counted, and
  range totals clip to the days they cover instead of pulling in whole
  sessions that only partially overlap them.

## What's new in 1.9.4

- The navigation bar's label slot is driven by a single animated value, so the
  five slot widths always sum to exactly one slot: the bar's width is constant
  by construction, at rest and mid-animation alike.
- Navigation buttons centre their contents properly — a fixed-height Material
  Surface places content at the top, which put every icon and label high in its
  circle. The top-bar timer pill gets the same fix.
- The selected tab's name is measured with the exact style it renders in (the
  theme's letter-spacing included), so longer labels — Arabic especially — no
  longer clip.
- Swiping updates the header and the nav highlight mid-swipe via the pager's
  target page; pages cross-fade only (no zoom), and the label travel uses a
  plain tween.
- Animation budget: a running timer now only recomposes the top-bar pill and
  the focus clock instead of the whole shell, and the flame stat tile no
  longer runs an infinite animation while idle.

## What's new in 1.9.3

- Every icon sits dead-centre in its circle: the glyph renderer measures each
  paragraph at the font's true height and centres it — verified against
  rendered pixels — and a per-glyph offset table computed from the font's
  ink boxes fixes asymmetric glyphs like the play triangle, coffee cup and
  edit pen.
- The leaderboard display name is an account setting now: chosen at sign-up,
  edited in Settings, removed from the Ranks tab — and a stale client can no
  longer quietly revert the board to a fallback name (Android, web and
  Quickshell all carry the same guard).
- Ranks refreshes quietly when you arrive: no loading row, and a re-save of
  your name always wins back the board.
- The focus-split donut draws with round arc ends; the rhythm chart's bars
  are full capsules with a sliver of air between stacked tags.
- Navigation polish: the floating bar is drawn as a background so no shape
  ever clips the selected end-tab, it solves its size to always fit the
  screen, and the selected tab pops its name open with springy scaling.

## What's new in 1.9.2

- The Today tab is now **Habits** — same list, a name that fits it.
- Swipe left and right to move between tabs. The navigation bar follows the
  Tomato style: a tinted floating pill of icons where the tab you are on
  becomes a solid button and pops its name open, and the widening reflow
  carries the highlight between tabs.
- Every icon glyph sits dead-centre in its circle: settings, play and pause
  no longer lean against the edges.
- The top bar keeps a stable height, so the running-timer pill can never
  nudge the page up and down when a session starts or ends.
- List entrances play once per screen and stay played: scrolling back up no
  longer flickers rows back in.
- All hairline one-pixel borders are gone; the celebration card is a calm
  rounded silhouette instead of a stretched twelve-sided polygon.
- The timer's play/pause button bounces on every toggle and spins between
  its two glyphs.
- Settings leads with its own Language section (with a proper translate
  icon), and the Arabic translation now covers composite strings — plans,
  greetings, stats, ranks and settings copy — instead of only fixed labels.

## What's new in 1.9.1

- A full sign-in screen now appears when the app opens signed out. Accounts are
  optional — “Continue without an account” keeps the local-only experience.
- Sessions are permanent: the encrypted refresh token is used at launch and
  while the app runs. Only a revoked password or deleted account signs you out;
  a dropped network never does.
- A new **Ranks** tab with Today / Week / Month focus leaderboards. Every
  account is listed by its **combined focus time across all public tags**.
  Each focus tag is still independently public or private; only public tag
  names and focus seconds are published — never habits, tasks or notes.
- Leaderboard publishes are conflict-safe across devices: each client merges
  the current row, keeps larger period totals, adopts the newest name/tag
  preference, and writes with a Firestore `updateTime` precondition.
- Stats now gives every tag its own focus rhythm, with an animated chart, goal
  line, streak and daily target.
- Motion throughout: page and top-bar transitions, animated navigation, a
  spring FAB, animated charts, progress bars, chips, completions and a confetti
  celebration when every habit is done.
- The top-bar sync icon is gone; account and sync state live in Settings.

## What was new in 1.6

- Accounts replace sync keys. Sign in or create an account with an email and
  password; the same account on the desktop app shares one private document.
- Tokens refresh automatically and are encrypted with the Android keystore.
  Google sign-in can be added to the same account later by linking an email.
- Live sync pushes about half a second after an edit and checks every five
  seconds. Idle checks only read; they do not rewrite the document.
- Visible name is now Panadoro; the package id stays `com.gritnative.app` so
  updates keep your data.

## What was new in 1.5

- Firebase sync replaces GitHub Gists.

## What was new in 1.4

- A productivity widget suite with three focused home-screen surfaces: Habits,
  Tasks and Pomodoro.
- Each widget adapts from a compact 2×2 glance to a resizable 4×4 surface,
  with Material You dynamic colour and AMOLED-aware backgrounds.
- Check in habits, complete tasks and start/pause/skip the Pomodoro timer
  directly from the home screen.
- A new **Settings → Productivity widgets** hub with one-tap pin buttons, plus
  date, timezone and locale refresh handling for widgets left on the home screen.

The 1.3 improvements remain: the neutral adaptive icon, continuous countdown
ring, richer timer notification and alarm/notification fallback controls.

## Features

- Habits, Tasks, Focus, Stats and Ranks tabs with Material You dynamic colour.
- Habits with schedules, targets, categories, notes and 12-week heatmaps.
- Tasks with priorities, subtasks, due dates and tags.
- Focus timer with short/long breaks, per-tag plans, session history and stats.
- Foreground timer notification with a live countdown, percentage, cycle and
  progress bar, plus an exact-alarm fallback.
- High-importance completion alarm with vibration and a direct audio fallback.
- AMOLED theme, system/light/dark modes and Monday/Sunday week starts.
- Firebase sync using the same Firestore document as quickshell.
- Three adaptive productivity widgets with direct habit, task and timer actions.

## Widgets

Open **Settings → Productivity widgets** and tap **Add** next to a surface. You
can also long-press the home screen → **Widgets** and choose any entry under
Panadoro. Android groups widgets by app, so the in-app hub is the curated
productivity collection.

- **Habits** — progress, streaks and one-tap check-ins.
- **Tasks** — today's queue, overdue work and completion.
- **Pomodoro** — live countdown with pause, resume and skip.

Resize any widget to switch between its compact and larger layouts. The focus
tile refreshes its countdown once per minute while a session is running, and all
widgets refresh on date, timezone and locale changes.

## Build

Requirements: JDK 17 and an Android SDK with build-tools and API 37.

```sh
./gradlew :app:assembleRelease
```

The APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

Install it with:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with this machine's Android debug key so it can be
installed locally. It is **not** signed with a Play Store key.

Run the JVM tests with:

```sh
./gradlew :app:testDebugUnitTest
```

## Timer alerts

On Android 13+ Panadoro asks once for notification permission after the first
frame. If it was skipped or denied, open **Settings → Alerts** to enable
notifications and exact alarms. If a completion alarm cannot use the system
notification sound, Panadoro plays the phone's alarm tone directly; make sure the
alarm volume is up and Do Not Disturb allows alarms.

## Account setup

1. Sign in on the screen that opens at launch, or open **Settings → Firebase
   sync** later.
2. Create an account with an email and password, or sign in to the same account
   you use on the desktop app.

The app reads and writes the private document `panadora/{uid}` in the `default`
Firestore database, and the rules only allow the signed-in user to touch their
own document:

```json
{
  "fields": {
    "payload": { "stringValue": "{ ...Store... }" },
    "revision": { "integerValue": "12" },
    "updatedAt": { "integerValue": "1760000000000" }
  }
}
```

## Leaderboard

Open **Ranks** and use the switches under **Public tags**. Each public tag's
focus seconds are mirrored to `panadora_leaderboard/{uid}`, and the rank uses
the combined total of those public tags for today, this week and this month.
Any signed-in account can read the board; only the owner can write or delete
their entry. Turning every tag private removes the entry, and signing out
removes it too. Concurrent device writes merge instead of replacing each
other. The rules are deployed from `~/panadora/firestore.rules`.

Sync is a fetch, record-by-record merge and full-snapshot update guarded by the
document's `updateTime` (or `exists=false` for the first write), so a concurrent
change is re-pulled instead of overwritten. Every record has `updatedAt` and a
deletion tombstone, so edits from two devices are merged instead of overwriting
each other. The ID and refresh tokens are encrypted with an Android-keystore
AES/GCM key and are never stored in the APK.
