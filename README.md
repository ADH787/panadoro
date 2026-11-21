# Panadoro

Habits, tasks and tagged focus in one quiet place. This repository contains all
three Panadoro clients sharing one Firebase backend:

| Client | Path | Where it runs |
| --- | --- | --- |
| Android | `android/` | signed APK, `com.gritnative.app` |
| Quickshell | `quickshell/` | the ii desktop shell |
| Web | `web/` | https://panadoro.web.app |

## One account everywhere

All three clients read and write the same private Firestore document
`panadora/{uid}`: a whole-store JSON snapshot with per-record `updatedAt` +
`device` stamps and deletion tombstones. Sync merges record by record and
guards writes with Firestore `updateTime` preconditions.

The **Ranks** tab is a separate opt-in mirror at
`panadora_leaderboard/{uid}`. Only focus tags the user marks public are
published. The rank is the **combined focus time across every public tag** for
today, this week or this month. Habits, tasks, notes and private tags never
leave the private document.

Leaderboard writes are conflict-safe across devices: each client reads the
current row, keeps the larger total for the same period, adopts the newest
display-name/public-tag timestamps, and writes with a
`currentDocument.updateTime` precondition. Concurrent devices retry and merge
instead of overwriting each other.

## Android

```bash
cd android
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleRelease
```

The APK is written to
`android/app/build/outputs/apk/release/app-release.apk`. Release `v1.8.0` has
the signed `panadoro-1.8.apk` attached.

## Web

```bash
cd web
python3 -m http.server 8123
```

Open `http://127.0.0.1:8123`. Use `?static=1` to freeze motion for screenshots
and `?tab=today|tasks|focus|stats|ranks` to deep-link a page.

## Firebase

Project `habit-tracker-bd354`, Firestore database `default`, hosting site
`panadoro`.

```bash
firebase deploy --only firestore:rules,hosting --project habit-tracker-bd354
```

## Quickshell

`quickshell/` mirrors the changed files under `~/.config/quickshell/ii/`.
`restore.sh` puts them back after an ii-dusk update; `reapply-updater.sh` also
re-arms the updater's protected-file list.

---

Based on Grit by shub39 and Tomato by nsh07.
