# Rutinify

Turn a routine written in plain language into local Android alarms.

You describe your day to any AI assistant (ChatGPT, Claude, Gemini…) using a prompt template the app gives you.
The assistant answers with a small JSON document; you paste it back into Rutinify, review it, and the app schedules
one exact alarm per activity. No backend, no account and no network access: everything stays on the phone.

> The app's user interface is in Spanish. Code, comments and commits are in English.

## How it works

1. **New routine** → *Copiar plantilla* copies the prompt template. Paste it into your AI assistant together with your
   routine ("6:45 walk the puppy, 7:00 breakfast, nap at 9:30…").
2. Copy the assistant's answer and tap *Pegar del portapapeles* → *Validar*. The JSON may come wrapped in text or in a
   Markdown code block; Rutinify finds it and explains any error in plain Spanish.
3. **Review**: check the date, exclude, edit or remove activities. Past activities are flagged and skipped.
4. *Programar alarmas* asks for the notification and exact-alarm permissions when needed, warns about another routine
   on the same date (replace or keep both) and schedules the alarms.
5. Each alarm shows a notification in its category channel with **Hecho** (done) and **+5 min** (snooze).

## Features

- Tolerant JSON extraction and validation with clear, typed error messages and warnings.
- Review screen: date picker, include/exclude, edit, remove with undo, "use tomorrow" suggestion.
- Exact alarms with `AlarmManager.setExactAndAllowWhileIdle`; falls back to a 10-minute window when the exact-alarm
  permission is denied.
- Order detail timeline: next alarm, status of each alarm, mark as done, delete with undo.
- Order actions: pause / resume, cancel all alarms, delete order, duplicate for another date.
- Robust rescheduling after reboot, time or time-zone change, app update, exact-alarm permission change and app start
  (alarms up to 15 minutes late still fire; later ones are skipped).
- Permissions screen and a Home banner while a permission is missing.
- Light and dark theme (dynamic color on Android 12+), adaptive and themed launcher icon.

## JSON contract (v1)

```json
{
  "schemaVersion": 1,
  "routine": { "name": "Rutina del cachorro", "date": "2026-11-05", "notes": "Primer día en casa" },
  "items": [
    { "time": "06:45", "dayOffset": 0, "title": "Sacar a hacer pis", "description": "Paseo corto",
      "durationMin": 10, "category": "paseo", "assignee": "Johan" },
    { "time": "02:00", "dayOffset": 1, "title": "Pis de madrugada", "description": null,
      "durationMin": 5, "category": "paseo", "assignee": null }
  ]
}
```

- `date` may be `null`: the app picks today (or tomorrow if nothing is left today) and says so.
- `dayOffset: 1` means "after midnight of the routine's day".
- Limits: 1–200 items, title ≤ 80 characters, ≤ 10 distinct categories, ≤ 500 pending alarms in the app.

The full contract is in [`specs/design.md`](specs/design.md) §4 and the prompt template in
[`app/src/main/res/raw/prompt_template.txt`](app/src/main/res/raw/prompt_template.txt).

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Show each alarm with its actions (Android 13+). |
| `SCHEDULE_EXACT_ALARM` | Ring at the exact minute (Android 12+). Optional: without it alarms may be a few minutes late. |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after a reboot. |

The app does **not** request `INTERNET`.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose with type-safe routes
- MVVM with a pure-Kotlin domain layer (one use case per action), single `:app` module
- Hilt, Room, kotlinx.serialization, Coroutines + Flow
- `AlarmManager` + `BroadcastReceiver`s (no WorkManager for firing)
- Tests: JUnit 4, Truth, Turbine, kotlinx-coroutines-test, Robolectric; coverage with Kover
- AGP 9 (built-in Kotlin), `minSdk 28`, `targetSdk 36`

## Project structure

```
app/src/main/java/com/mirkwood/rutinify
├── domain/     models, use cases, repository and scheduler interfaces (no Android imports)
├── data/       Room, JSON importer, AlarmManager scheduler, notifications, receivers
├── di/         Hilt modules
└── ui/         Compose screens: home, newroutine, review, orderdetail, permissions
specs/          requirements.md (what), design.md (how), tasks.md (work order), manual-test.md
```

## Build and test

Requires Android Studio (or JDK 17+ and the Android SDK 36). On Windows use `gradlew.bat`.

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # JVM unit tests (Room and receivers via Robolectric)
./gradlew lintDebug            # Android lint
./gradlew koverVerify          # line coverage ≥ 80 % on domain + JSON importer
./gradlew installDebug         # install on a connected device or emulator
```

## Status

The MVP is complete: all 32 tasks in [`specs/tasks.md`](specs/tasks.md) are done, 483 unit tests pass, lint has no
project warnings, coverage of the domain and importer is 99.2 %, and the manual checklist in
[`specs/manual-test.md`](specs/manual-test.md) passes on an Android 16 emulator.

Not in the MVP (see `specs/design.md` §15): recurring routines, a home-screen widget, calling an AI API directly,
`.ics` export, and other UI languages.

## How this project is developed

Spec-driven: every change is backed by a requirement (`R-x.y`) and a task (`T-xx`), tests come first for domain logic,
and minor decisions are logged in `specs/design.md` §14. See [`CLAUDE.md`](CLAUDE.md) for the full working rules.

## License

[MIT](LICENSE)
