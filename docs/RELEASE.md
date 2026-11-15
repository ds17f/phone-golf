# Release guide

This guide tells you how to send Phone Golf to Google Play and to the App
Store. The release workflows are in `.github/workflows`.

## How to make a release

1. Add a section for the version to `androidApp/CHANGELOG.md` or
   `iosApp/CHANGELOG.md`. Use the heading `## [1.2.3]`.
2. Commit the change and push it to `main`.
3. Make a tag and push it:
   - Android: `git tag android/v1.2.3 && git push origin android/v1.2.3`
   - iOS: `git tag ios/v1.2.3 && git push origin ios/v1.2.3`

The Android workflow builds the signed bundle, makes a GitHub release and sends
the bundle to the internal test track of Google Play. The iOS workflow builds
the store app, sends it to TestFlight and makes a GitHub release.

The version name comes from the tag. The build number is the run number of the
workflow plus 100, thus each upload has a larger number.

## Repository variables

| Variable              | Use                                                        |
| --------------------- | ---------------------------------------------------------- |
| `IOS_TEAM_ID`         | The Apple team ID. The iOS release needs it.                |
| `API_BASE_URL`        | Optional. The server address in the store apps. The default is `https://phonegolf.makersundays.com`. |
| `PLAY_RELEASE_STATUS` | Optional. The default is `draft`. Set it to `completed` after the app is public in Google Play. |
| `LINUX_RUNNER`        | Optional. The label of your own Linux runner.               |
| `MACOS_RUNNER`        | Optional. The label of your own Mac runner.                 |

## Android secrets

| Secret                      | Content                                             |
| --------------------------- | --------------------------------------------------- |
| `ANDROID_KEYSTORE_BASE64`   | The upload keystore, in base64.                     |
| `ANDROID_KEYSTORE_PASSWORD` | The password of the keystore.                       |
| `ANDROID_KEY_ALIAS`         | The alias of the key.                               |
| `ANDROID_KEY_PASSWORD`      | The password of the key.                            |
| `PLAY_STORE_JSON_BASE64`    | The key of the Google Play service account, in base64. |

On your computer, the same data is in `.secrets/` (not in git):
`.secrets/release.jks`, `.secrets/keystore.properties` and
`.secrets/play-store.json`. With these files, `./gradlew bundleRelease` makes a
signed bundle.

CAUTION: KEEP A COPY OF `.secrets/release.jks` AND ITS PASSWORD IN A SAFE
PLACE. IF YOU LOSE THE KEY, YOU MUST ASK GOOGLE FOR A NEW UPLOAD KEY.

### Google Play: steps to do one time

1. In the Play Console, make the app. The package name is `com.phonegolf.app`.
2. Accept Play App Signing.
3. In "Users and permissions", give the service account access to the app.
4. Complete the store entry: the description, the screenshots, the data safety
   form and the content rating. The privacy policy address is
   <https://ds17f.github.io/phone-golf/privacy.html>.
5. Complete the declaration for the permission `PACKAGE_USAGE_STATS`. The app
   uses it only to count the unlocks of each day.
6. Push the first tag. The first upload of a new app can fail through the API.
   If it fails, upload the bundle from the GitHub release in the Play Console
   one time.

## iOS secrets

| Secret                         | Content                                          |
| ------------------------------ | ------------------------------------------------ |
| `IOS_CERTIFICATE_BASE64`       | The Apple Distribution certificate (`.p12`), in base64. |
| `IOS_CERTIFICATE_PASSWORD`     | The password of the `.p12` file.                 |
| `IOS_PROFILE_APP_BASE64`       | The App Store profile `PhoneGolf AppStore`, in base64. |
| `IOS_PROFILE_REPORT_BASE64`    | The App Store profile `PhoneGolf PickupsReport AppStore`, in base64. |
| `APP_STORE_CONNECT_KEY_ID`     | The ID of the App Store Connect API key.         |
| `APP_STORE_CONNECT_ISSUER_ID`  | The issuer ID of the API key.                    |
| `APP_STORE_CONNECT_KEY_BASE64` | The API key (`.p8`), in base64.                  |

### Apple: steps to do one time

1. Ask Apple for the Family Controls (Distribution) entitlement for the two
   bundle IDs. Do this first, because the approval can take days or weeks.
   The form is at <https://developer.apple.com/contact/request/family-controls-distribution>.
2. On the Apple Developer site, make the App Group `group.com.phonegolf.app`.
3. Make two App IDs: `com.phonegolf.app` and
   `com.phonegolf.app.PickupsReport`. For each one, set the capabilities
   Family Controls and App Groups, and select the App Group.
4. After the approval, make two App Store profiles with these names:
   `PhoneGolf AppStore` and `PhoneGolf PickupsReport AppStore`. Use the Apple
   Distribution certificate of the team.
5. Put the two profiles in the secrets `IOS_PROFILE_APP_BASE64` and
   `IOS_PROFILE_REPORT_BASE64`: `base64 -w0 file.mobileprovision | gh secret set NAME`.
6. In App Store Connect, make the app with the bundle ID `com.phonegolf.app`.
7. Complete the store entry: the description, the screenshots, the privacy
   answers and the age rating. The privacy policy address is
   <https://ds17f.github.io/phone-golf/privacy.html>.
8. Push the first tag.

## Checks

`.github/workflows/ci.yml` runs for each pull request and each push to `main`.
It runs the API tests, the Android tests and build, and the iOS simulator
build.
