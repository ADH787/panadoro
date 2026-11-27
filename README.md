# Panadoro

Habits, tasks and tagged focus in one quiet place. Panadoro is free and
open-source software, released under the
[GNU General Public License version 3](LICENSE) (GPL-3.0-only).

Panadoro is inspired by **[Grit](https://github.com/shub39/Grit)** and
**[Tomato](https://github.com/nsh07/Tomato)** — see [Credits](#credits).

This repository holds the three clients that share the same Firebase backend:

- **Android** (`android/`) — Kotlin/Jetpack Compose APK with a launch login,
  permanent session, Ranks and a full motion pass. **2.0.0** brings the new
  hero cards, Titan One app font, the tomato artwork icon and the
  `com.panadoro.app` application id.
- **Quickshell** (`quickshell/`) — the desktop Panadoro app inside the ii shell.
- **Web** (`web/`) — the same app in a browser, installed at
  **https://panadoro.web.app**. Flat responsive UI, English/Arabic (RTL),
  skeleton loading and DOM-morphing renders so live sync does not flicker.

## 2.0.0 highlights

- Cleaner tabs: centred larger titles, no subtitles or settings descriptions,
  and fewer summary cards. Tasks and Habits are one straight list each, with
  finished rows staying visible and gliding to the end.
- New Habits/Tasks hero with a live drifting watermark and progress ring.
- Stats: the rhythm chart fills its card and Daily hides the charts.
- Focus: tags collapse behind the small arrow beside the active tag.
- Settings: any tab can be hidden, with a guard that keeps at least one visible;
  larger row icons; new Panadoro colour profile; App font switch between
  Titan One (default) and the phone's system font.
- The supplied tomato-reading artwork is the launcher icon; the app id is
  `com.panadoro.app` and the build is 2.0.0 (versionCode 25).

## One account, three clients

All clients read and write the same private Firestore document
`panadora/{uid}`. The document is a whole-store JSON snapshot with per-record
`updatedAt` + `device` stamps and deletion tombstones. Sync merges record by
record and guards writes with Firestore `updateTime` preconditions.

The **Ranks** page is a separate, opt-in mirror at
`panadora_leaderboard/{uid}`. Only the focus tags a user marks public are
published; the score shown is the **combined focus time across all public tags**
for today, this week or this month. Habits, tasks, notes and private tags never
leave the private document.

The leaderboard display name and public-tag selection are also stored inside
the private synced document. Save them once on any device and the other clients
adopt them on the next sync, with per-field timestamps preventing a stale
device from undoing a newer choice.

Leaderboard writes are conflict-safe: every client reads the current row,
keeps the larger total for the same period, keeps the newest display-name and
public-tag preference, and writes with a `currentDocument.updateTime`
precondition. Concurrent devices retry instead of overwriting each other.

## Midnight-accurate focus stats

A focus session that runs past midnight is split at every local midnight it
crosses, and each day is credited with the part that actually happened on it:
23:50→00:20 gives ten minutes to the first day and twenty to the next. The
split drives every day-bucketed number — today's focus, streaks, the rhythm
charts, the heatmaps, the time-of-day buckets and the Ranks day/week/month
totals — and it is exact: a paused session's recorded seconds are spread over
its wall-clock span in proportion to each part's length, so the parts always
add back up to the session's recorded duration.

## Firebase

- Project: `habit-tracker-bd354`
- Firestore database: `default`
- Web: https://panadoro.web.app
- Hosting config + rules: `firebase/` (`firebase.json`, `firestore.rules`)

Deploy rules and web:

```bash
firebase deploy --only firestore:rules,hosting --project habit-tracker-bd354
```

## Android

```bash
cd android
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleRelease
```

The signed APK is written to `android/app/build/outputs/apk/release/app-release.apk`.
The current release is **[2.0.0](https://github.com/ADH787/panadoro/releases/latest)**.

## Quickshell

The files under `quickshell/` are drop-in copies for
`~/.config/quickshell/ii/`. They are also protected by the shell updater’s
carry-over list. `scripts/restore-quickshell.sh` restores them after an
ii-dusk update.

## Web development

```bash
cd web
python3 -m http.server 8123
```

Open `http://127.0.0.1:8123`. Add `?static=1` to freeze animations for
screenshots, and `?tab=today|tasks|focus|stats|ranks` to deep-link a page.

## Credits

Panadoro builds on the ideas and design of two excellent open-source Android
projects:

- **[Grit](https://github.com/shub39/Grit)** by
  [shub39](https://github.com/shub39) — a simple todo list and habit tracker.
  Grit inspired Panadoro's habits, tasks, widgets and Material 3 design.
- **[Tomato](https://github.com/nsh07/Tomato)** by
  [nsh07](https://github.com/nsh07) — a minimalist, data-oriented Pomodoro
  timer for Android and desktop. Tomato inspired Panadoro's focus timer,
  Material 3 Expressive styling and timer stats.

Thank you to both authors and all of their contributors. Grit and Tomato are
licensed under the GNU General Public License v3.0, and Panadoro is released
under the same license; follow the links above for their source and full
license text.

## Contributors

- **ADH787** — creator and maintainer.
- **DeepSeek** — AI pair programmer; contributed code, debugging and
  documentation across the Android, Quickshell and web clients.

See [CONTRIBUTORS.md](CONTRIBUTORS.md) for the full list.

## License

Copyright (C) 2026 ADH787.

Panadoro is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, version 3 of the License.

Panadoro is distributed in the hope that it will be useful, but **without any
warranty**; without even the implied warranty of merchantability or fitness for
a particular purpose. See the [GNU General Public License version
3](LICENSE) for the full text.

Third-party components keep their own licenses: the Titan One font is under the
SIL Open Font License 1.1, morphdom is under the MIT license, Lucide icons are
under the ISC license, and the Gradle wrapper is under the Apache License 2.0.
