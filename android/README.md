# Panadoro 1.9.2

Native Kotlin/Jetpack Compose build of the merged Panadoro habit tracker and
Tomato focus timer. The UI data model and Firebase wire format are compatible
with the quickshell app, so both clients stay in sync.

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
