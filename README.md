# Panadoro

Habits, tasks and tagged focus in one quiet place. This repository holds the
three clients that share the same Firebase backend:

- **Android** (`android/`) — Kotlin/Jetpack Compose APK with a launch login,
  permanent session, Ranks and a full motion pass.
- **Quickshell** (`quickshell/`) — the desktop Panadoro app inside the ii shell.
- **Web** (`web/`) — the same app in a browser, installed at
  **https://panadoro.web.app**. Flat responsive UI, English/Arabic (RTL),
  skeleton loading and DOM-morphing renders so live sync does not flicker.

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

Leaderboard writes are conflict-safe: every client reads the current row,
keeps the larger total for the same period, keeps the newest display-name and
public-tag preference, and writes with a `currentDocument.updateTime`
precondition. Concurrent devices retry instead of overwriting each other.

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
The current build is `android/panadoro-1.8.apk`.

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
