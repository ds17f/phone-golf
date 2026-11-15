# CLAUDE.md

This file gives guidance to Claude Code for this repository. Read `README.md`
for the game rules and the platform limits.

## Commands

- API tests: `make api-test`. Type check: `make api-typecheck`.
- API and web on port 3100: `make api-dev`.
- Android build and unit tests: `make android-build`, `make android-test`.
- iOS: `make ios-build`, `make ios-device`, `make ios-check`. On Linux, these
  commands run on the Mac in `IOS_HOST` over ssh (the openmat method). The
  settings are in `.env` (see `.env.example`). Do not ask the user to ssh to
  the Mac.

## Decisions (do not change without a request from the user)

- Scores are literal counts. Do not convert counts to golf strokes.
- Players set their own handicap. The app only suggests a number.
- The game uses an honor system. Do not add anti-cheat features.
- iPhone players type their pickups. Apple does not let an app read Screen
  Time data. Do not use private APIs or sandbox workarounds, because App Review
  will reject them.
- The server never converts time zones. The device sends local calendar days
  (`YYYY-MM-DD`) and the `X-Local-Date` header.
- A handicap change starts on the next day.
- A manual score is not replaced by an automatic score.
- The round screen is the home screen. It shows the scoreboard strip (hole,
  target, round to par) and the paper scorecard. Do not put lists or settings
  on the round screen. They go in Settings, behind the gear icon.
- The look is a golf tournament: a green board with cream plaques, and a paper
  scorecard (Out, In, Tot). The scorecard fits the width of the phone with no
  horizontal scroll. Each cell shows the literal count. A circle means under
  the target (handicap + par). A square means over. Red means under par, as in
  golf. Keep the web, Android and iOS versions the same.
- Solo mode (no server) is the default on Android and iOS. "Tee off" starts a
  solo round with one tap. A player plays one round at a time. Group play is an
  option in Settings, and groups need the server. A change of mode does not
  delete the solo data. Do not add peer-to-peer or cloud-file sync without a
  request from the user.
- On iPhone with Screen Time access, iOS draws the round screen: the report
  extension draws the strip, the scorecard and the leaderboard, with the
  pickups of each day. The player types nothing to see the card. The app gives
  the round to the extension through the App Group (`CardSpec`). The app cannot
  read the pickups.
- For a group, the player signs the card ("Sign your card"): the player types
  each count, and then the server has it. A count that is not signed shows in
  grey (pencil). Do not use red for this: red means under par. The leaderboard
  uses signed scores only. Solo needs no signature.
- The "Sign your card" button is between the strip and the scorecard. It opens
  a table of the holes with no signature, and the hole of today.
- The bottom of the round screen must be from the app, not from the report. A
  report in a scroll area of the app stops the swipe up that closes the app.

## Scoring rules are in three places

The scoring rules exist in three files. If you change a rule, change all three
files and their tests:

- `api/src/scoring.ts` (server)
- `androidApp/.../Scoring.kt` and `LocalBackend.kt` (Android solo mode)
- `iosApp/PhoneGolf/Scoring.swift` and `LocalBackend.swift` (iOS solo mode)

## Code map

- `api/src/scoring.ts`: the scoring rules. Pure functions with tests.
- `api/src/app.ts`: all routes. `/api/*` needs `Authorization: Bearer <player key>`,
  except `POST /api/players` and `GET /api/health`.
- `api/src/db.ts`: the SQLite schema.
- `web/app.js`: the web client. Hash routes: `#/` (the current round),
  `#/group/<id>`, `#/course/<id>`, `#/settings`.
- `androidApp/.../UnlockCounter.kt`: counts `KEYGUARD_HIDDEN` events.
- `androidApp/.../SyncWorker.kt`: sends the last 7 days about each hour.
- `iosApp/Shared/`: code for the app and the report extension. `GolfViews.swift`
  has the strip, the paper scorecard and the leaderboard. `CardSpec.swift` is
  the round that the app gives to the extension.
- `iosApp/PickupsReport/`: the report extension. It draws the round
  (`ScorecardReportView`) and the pickups of one day (`PickupsView`).
- `iosApp/PhoneGolf/Views/RoundView.swift`: the home screen. It shows the
  report, or the app-drawn round when there is no Screen Time access.
- `iosApp/PhoneGolf/Views/SignCardSheet.swift`: the sign table. Each row has
  the pickups of one day and a field.
- `.github/workflows/ci.yml`: the checks. The repository variables
  `LINUX_RUNNER` and `MACOS_RUNNER` select your own runners.
- `.github/workflows/android-release.yml`, `ios-release.yml`: the store
  releases. A tag `android/v*` or `ios/v*` starts one. See `docs/RELEASE.md`.
- `docs/`: the GitHub Pages site, with the privacy policy.
- `Backend` (Kotlin interface, Swift protocol): the screens use it. `Api` is
  the server. `LocalBackend` is solo mode.

## Android emulator

Always start the emulator with `-gpu host`. The emulator lock screen is off by
default. Turn it on with `adb shell locksettings set-disabled false`, or the
app counts no unlocks.
