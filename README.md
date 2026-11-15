# Phone Golf

Phone Golf is a game that helps you use your phone less. Each day is one hole.
Your score is the number of times that you unlocked your phone. A low score is
a good score. You play in a group with friends and family.

## Game rules

- **Course.** A course has 18 holes. One hole is one day. The 18 days are
  consecutive.
- **Gross.** Gross is the number of unlocks (Android) or pickups (iPhone) on
  one day. The number is literal. The app does not convert it.
- **Handicap.** Your handicap is your daily baseline. It is the number of
  unlocks that you need on a normal day. You set it when you join a group.
- **Net.** Net is gross minus handicap.
- **Par.** Each hole has a par. The course creator sets a weekday par and a
  weekend par. The creator can change the par of each hole later. Par 0 means
  "stay at your baseline".
- **To par.** To par is net minus par. The leaderboard sorts players by the
  total to par. The lowest total wins.
- **Missed days.** A day with no score does not count. The "Thru" column shows
  the number of holes that each player played.
- **Handicap changes.** The app can suggest a handicap. The suggestion is the
  average of your best half of the days that you scored in the last 14 days. It
  needs a minimum of 3 days. You can accept the suggestion or type a different
  number. A change starts on the next day. You cannot change the score of a day
  that is finished.
- **Honor system.** Players can lie. The game accepts this risk. Golf players
  also keep their own scorecards.

## Two modes

The app starts in solo mode. The first screen has one button, "Tee off". The
button makes a solo player and starts a round of 18 days. The player does not
make a course or a group. A player plays one round at a time.

| Mode | Server | Groups | Where the data is |
| ---- | ------ | ------ | ----------------- |
| Solo (default) | Not necessary | No. One player only. | In one JSON file on the phone |
| Group | Necessary | Yes. Players join with an invite code. | On the Phone Golf server |

Solo mode uses the same rules as the server: handicap, par, the handicap
suggestion, and the change that starts on the next day. Solo mode works with no
network connection. Solo scores do not move to a server.

To play with a group, select "Play with a group" in Settings. The solo card
stays on the phone. To go back to the solo card, select "Play solo again" in
Settings. "Start over" in Settings deletes the solo data.

## The screens

The phone apps have three primary screens:

- **Welcome.** The handicap field and the "Tee off" button. The player sees
  this screen only one time.
- **Round.** This is the home screen. It shows a scoreboard strip (the hole,
  the target for today, the round to par) and the paper scorecard. The
  scorecard fits the width of the phone. In a group, the leaderboard is below
  the scorecard.
- **Settings.** The handicap, the name, group play, the past rounds, and
  "Start over". The gear icon opens this screen.

The home page of the website shows the same scoreboard strip, scorecard and
leaderboard. The website has no solo mode.

## How each platform gets the score

| Platform | Number   | How the app gets it                                                    |
| -------- | -------- | ---------------------------------------------------------------------- |
| Android  | Unlocks  | Automatic. The app reads `KEYGUARD_HIDDEN` usage events about each hour. |
| iPhone   | Pickups  | iOS shows it on the card. For a group, the player types it (signs the card). |
| Web      | Either   | The player types it.                                                   |

### Android

Android records an event each time the lock screen closes. This is the same
count that Digital Wellbeing shows. A look at a notification on the lock screen
is not an unlock. The app needs the "Usage access" permission. The user gives
this permission in Settings.

The app sends the last 7 days each time that it runs. Thus, a missed run
corrects itself. If Android has no usage data for a day, the app does not send
a score for that day. A score that the player typed is not replaced by an
automatic score.

### iPhone

iOS does not count unlocks. The nearest number is "pickups". Apple does not
let an app read Screen Time data. A `DeviceActivityReport` extension can read
the data and show it on the screen. The extension runs in a sandbox. It cannot
send the data to the app or to the network.

Thus, the extension draws the round screen of the iPhone app: the scoreboard
strip, the scorecard and the leaderboard. The scorecard shows the pickups of
each day of the round. The player types nothing to see the card. In solo mode,
this is the full game.

A group needs the scores on the server, and the app cannot read them. Thus, in
a group, the player signs the card. The "Sign your card" button is between the
strip and the scorecard. It opens a table with one row for each hole that has
no signature. Each row shows the pickups, and the player types the number.
The player can also sign for today before the day is finished.
A count that the player did not sign for shows in grey, as a pencil mark. The
leaderboard uses signed scores only.

A notification each morning tells the player to sign the card for yesterday.
The app has a "Why do I type it?" screen that explains this limit to the
player.

## Project layout

```
api/          TypeScript API (Fastify, built-in node:sqlite). Also serves web/.
web/          Web leaderboard and score entry. Plain HTML, CSS and JS. No build step.
androidApp/   Android app (Kotlin, Jetpack Compose, WorkManager).
iosApp/       iOS app (SwiftUI) and the PickupsReport extension. XcodeGen project.yml.
Dockerfile    One image for the API and the web leaderboard.
```

## Sign-in (group mode)

The game does not use accounts. When a player starts, the API makes a secret
player key. The app keeps the key. To use the same player on a different
device, copy the key from Settings and paste it on the other device. If a
player loses the key, the player loses that identity.

## Local development

You need Node.js 22.13 or later, because the API uses `node:sqlite`.

1. Install the API packages: `make api-install`.
2. Start the API and the web leaderboard: `make api-dev`.
3. Open <http://localhost:3100>.
4. Run all tests: `make test`.

The dev port is 3100, because other local apps often use port 3000.

### Android

1. Start the emulator: `make android-emulator`. The command uses `-gpu host`,
   because SwiftShader crashes on this machine.
2. Install the debug app: `make android-install`. The debug app connects to
   `http://10.0.2.2:3100`, which is the host as the emulator sees it.
3. Give usage access from the app, or use this command:
   `adb shell appops set com.phonegolf.app GET_USAGE_STATS allow`.

On the emulator, the lock screen is off by default. With no lock screen there
are no unlock events. Turn the lock screen on with
`adb shell locksettings set-disabled false`.

### iOS

You must build the iOS app on a Mac with Xcode 15 or later. On Linux, the
`make ios-*` commands copy the code to the Mac and run there over ssh. This is
the same method as the openmat monorepo.

1. Copy `.env.example` to `.env`. Set `IOS_HOST` to the Mac (`user@host`).
   Passwordless ssh (a key) to the Mac must work.
2. Set `IOS_KEYCHAIN_PASSWORD` in `.env` to the login password of the Mac
   user. `make ios-device` uses it to unlock the keychain of the Mac, so that
   the build can sign the app over ssh.
3. Copy `iosApp/Config/Local.xcconfig.example` to
   `iosApp/Config/Local.xcconfig`. Set `DEVELOPMENT_TEAM`.
4. Run `make ios-check`. It lists each problem and its fix.

| Command            | What it does                                              |
| ------------------ | --------------------------------------------------------- |
| `make ios-build`   | Builds the app for the iOS simulator.                     |
| `make ios-sim`     | Builds the app and starts it in the iOS simulator.        |
| `make ios-device`  | Builds, installs and starts the app on the connected iPhone. |
| `make ios-check`   | Checks the Mac and the `.env` settings.                   |

The API address of the app is `PG_API_BASE_URL` in
`iosApp/Config/Base.xcconfig`. The default is
`https://phonegolf.makersundays.com`.

The Family Controls entitlement works in development builds without approval.
For TestFlight and the App Store, Apple must approve the entitlement. Send the
request early, because the approval can take days or weeks. Use the "Family
Controls (Distribution)" request form on the Apple Developer site.

The Screen Time report does not work in the iOS Simulator. Use a real iPhone to
test the pickups report.

## Home server

You can run the API on a home server. Set `HOMELAB_HOST` and `GOLF_URL` in
`.env` (see `.env.example`).

- Deploy the current code: `make homelab-deploy`.
- Build and install the Android app for the home server:
  `make android-install-home ADB_DEVICE=<serial>`.

## Public deployment

The image runs on the same server as deadly, emma-shop and the Starlight Loft.
The deadly Caddy server is the only process that uses ports 80 and 443.

1. On the host, make the shared network: `docker network create web`.
2. Start the stack: `docker compose up -d`. The container `phone-golf` joins
   the `web` network. It does not use host ports.
3. In the deadly repo, add one site block to the `Caddyfile`. Use the same
   pattern as `SHOP_ADDRESS`:

   ```caddyfile
   {$GOLF_ADDRESS} {
   	reverse_proxy phone-golf:3000
   }
   ```

4. In the deadly `docker-compose.yml`, add
   `GOLF_ADDRESS=${GOLF_ADDRESS:-golf.localhost}` to the Caddy environment.
5. Point a DNS A record for the golf host to the server.
6. The release apps use `https://phonegolf.makersundays.com`. To use a
   different address, set the repository variable `API_BASE_URL`.

## Releases and the stores

`docs/RELEASE.md` tells you how to make a release, and lists the secrets and
the store steps. A tag `android/v1.2.3` or `ios/v1.2.3` starts a release.

The privacy policy is `docs/privacy.html`. GitHub Pages serves it at
<https://ds17f.github.io/phone-golf/privacy.html>.

## License

MIT. See `LICENSE`.

The SQLite database is in the `phone_golf_data` volume.

## Status

| Part          | Status                                                                 |
| ------------- | ---------------------------------------------------------------------- |
| API           | Done. 12 tests pass.                                                   |
| Web           | Done. Tested in a browser at desktop and phone width, light and dark.  |
| Android       | Done. Tested on an emulator: unlock count, sync, group, course, leaderboard, solo mode. |
| iOS           | Done. Tested on an iPhone. The store build needs the Family Controls approval from Apple. |
| Docker        | Done. The image builds and runs.                                       |
| Deployment    | Not done. It needs the Caddy change in the deadly repo and the DNS record for `phonegolf.makersundays.com`. |
| Stores        | Not done. The release workflows are ready. See `docs/RELEASE.md`.      |

## Ideas for later

- Other formats: match play, skins, and a tournament across groups.
- Other numbers: screen time, notifications.
- A recovery method for a lost player key.
- A screenshot reader (OCR) on iPhone, to remove the step where the player
  types the number.
