# Android APK (Lecturer + Student in one native app)

This repo contains two web apps and **one native Android app**:

| Portal | Web source (unchanged) | Native screen code |
| --- | --- | --- |
| Lecturer | repo root (`src/`) | `android/.../ui/LecturerScreens.kt`, `LecturerClass.kt` |
| Student | `student-frontend/` | `android/.../ui/StudentScreens.kt` |

The Android app is **fully native**: Kotlin + Jetpack Compose + Material 3.
There is no WebView, no bundled JavaScript and no web build step. The web
apps remain in the repo as the design/behaviour reference, and every
screen, label, colour and flow mirrors them.

**One APK, two roles.** On launch the app shows a role picker
(*Lecturer* / *Student*). Tapping a role opens that portal — sign in or
create an account — and each portal then operates separately:

- Separate logins and sessions (`lecturer_auth` / `student_auth` storage;
  signing into one never signs into the other).
- Separate screens and navigation (Lecturer: Home, Students, Courses,
  Class, Setting. Student: Home, Class, Course, Setting + Notifications).
- A **← Choose role** link on the login screen switches portals.

- App name: **SmartIQ** (package `com.smartattendance.app` is unchanged so existing installs keep updating)
- Package ID: `com.smartattendance.app`
- Minimum Android: 8.0 (API 26); target/compile SDK 34
- Backend: configured at build time, not hardcoded. `data/Api.kt` reads
  `BuildConfig.API_BASE_URL`, generated from the `API_BASE_URL` Gradle property /
  environment variable (CI uses the repository variable of the same name).
  Default when unset: `https://attendance-system-33pl.onrender.com`.
  Point it at the Cloudflare domain: `API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh`.
  See `docs/DOMAIN_AND_API_SECURITY.md`.

## What is native now

| Web feature | Native implementation |
| --- | --- |
| REST client, bearer tokens, 401 handling | `data/Api.kt` (`ApiClient`, `ApiException`) |
| Per-portal sessions | `data/AuthStore.kt` |
| Bluetooth node verification (`shared/ble.tsx`) | `data/BleScanner.kt` — real `BluetoothLeScanner`, throttled `POST /api/attendance/ble-observation`; never simulated |
| In-app alerts (`useNotificationPolling`) | 15 s polling in `ui/StudentScreens.kt` → `NotifyHelper` |
| Background alerts when closed | `data/NotificationService.kt` foreground service, 30 s polling with the restricted device token from `POST /api/student/notifications/device-token` (student only) |
| "Export my activity" PDF (`exportActivity.ts`) | `data/PdfExport.kt` — framework `PdfDocument` → Downloads (MediaStore on Android 10+) |
| Screens & design (`src/shared/styles.css`) | `ui/Theme.kt` (same palette), `ui/Components.kt` (cards, badges, buttons, notices, dialogs), `ui/RolePicker.kt`, `ui/AuthScreens.kt`, `ui/LecturerScreens.kt`, `ui/LecturerClass.kt`, `ui/StudentScreens.kt`, `ui/BlePanel.kt` |

Bluetooth sightings are only sent when a real classroom node is seen. The
lecturer flow mirrors the web exactly: choose course → choose classroom →
verify presence with Bluetooth → start class → live roster → **Class over**
(releases the room, 6-hour manual-correction window).

## Get the APK without a local Android setup

The APK is built by GitHub Actions (`.github/workflows/build-apk.yml`):

1. Open the repo on GitHub → **Actions** → **Build APK** → **Run workflow**
   (or open a pull request — the workflow runs automatically).
2. Wait for the run to finish (a few minutes).
3. Download `app-debug.apk` from the repository’s **latest release**, or open
   the finished run → **Artifacts** → download `smart-attendance-apk`.

Debug APKs are signed with the standard debug key and install directly on
any Android 8.0+ phone.

The `latest` release is updated automatically for successful runs on `main`
and manual workflow runs. Pull requests only upload a temporary workflow
artifact and do not publish a release.

> If a run fails instantly with *"recent account payments have failed or
> your spending limit needs to be increased"*, that is GitHub billing for
> private repositories — not a code problem. Add a payment method / raise
> the spending limit in **Settings → Billing**, or build locally below.

## Build locally

Prerequisites: JDK 17, Android SDK with platform 34 and build-tools 34.0.0
(Android Studio installs these). No Node.js needed.

```bash
./scripts/build-apk.sh
# or
cd android && ./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Or open the `android/` folder in Android Studio and press **Run**.

## Install on a phone

1. Copy the APK to the phone (USB, Drive, etc.) and open it.
2. Allow "Install unknown apps" for the browser/file manager if prompted.
3. Open **SmartIQ**, choose **Lecturer** or **Student**, and sign
   in (or create an account) with the same accounts as the web portals.

## Permissions (requested by the app)

| Permission | Why |
| --- | --- |
| Bluetooth scan/connect (Android 12+) or Location (older) | Find the classroom ESP32 node for attendance verification |
| Notifications (Android 13+) | "Class started / attendance finalised" alerts |
| Battery-optimisation exemption (student, optional) | Keep background class alerts working when the app is closed |

On Android 11 and older, Location must be turned **on** in system settings
for Bluetooth scanning to work (Android requirement, not app behaviour).

## Notifications arriving late?

The backend runs on Render's free tier, which sleeps after inactivity. The
first request after a sleep can take ~30–50 seconds while the server wakes,
so an alert can arrive late even though the app polls on time. Opening the
app (or any request) wakes the backend.

## Release builds

`BUILD_TYPE=release ./scripts/build-apk.sh` (or `cd android && ./gradlew assembleRelease`)
builds the release variant. CI does the same in the **Build release APK (test)** job and
uploads `smart-attendance-release-apk`; run it from *Actions → Build APK → Run workflow*.

The release variant is currently **debug-signed**: no keystore is configured yet, so
`android/app/build.gradle.kts` falls back to the debug key to keep the APK installable for
testing. That APK is for testing only — a debug-signed build must not be distributed, and it
cannot be updated later by a properly signed build with the same `applicationId`.

### Adding the real keystore (later)

1. Generate one (once, keep it safe — losing it means you can never update the app):
   `keytool -genkeypair -v -keystore release.jks -alias attendance -keyalg RSA -keysize 2048 -validity 10000`
2. Local builds: put the values in `android/local.properties`, which is git-ignored:
   ```properties
   KEYSTORE_FILE=release.jks          # relative to android/
   KEYSTORE_PASSWORD=…
   KEY_ALIAS=attendance
   KEY_PASSWORD=…
   ```
3. CI: add repository secrets `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`, and add a "Restore keystore" step to the `release` job that
   decodes `KEYSTORE_BASE64` into `$RUNNER_TEMP/release.jks` and exports `KEYSTORE_FILE`.

No Gradle change is needed for either route: the signing config reads those variables and
switches from the debug key to the real keystore automatically (the build log prints which one
it used).

Version numbers can be raised at build time: `./gradlew assembleRelease -PversionCode=2 -PversionName=1.1.0`.

## Troubleshooting

- **"Bluetooth verification needs the SmartIQ Android app"**:
  that message only appears in a mobile browser. Use the installed app.
- **Node never detected**: check Bluetooth is on, permissions are granted,
  and (Android 11 and older) Location is on. The ESP32 node must be
  powered and advertising nearby.
- **No background alerts (student)**: allow notifications, and exempt the
  app from battery optimisation in system settings.
- **Gradle can't resolve dependencies offline**: the first build needs
  internet access to `dl.google.com` / Maven Central.

## Branding

Launcher icon and in-app logo come from `android/branding/`: `smartiq-logo.png` is the source
artwork, `smartiq-emblem.png` the cropped emblem (wordmark, tagline and the wifi marks removed),
`play-store-icon-512.png` the listing icon. Everything under `res/mipmap-*` and
`res/drawable-nodpi/smartiq_emblem.png` is generated - re-run
`python3 android/branding/generate-icons.py` after replacing the artwork. See
`android/branding/README.md` for the sizes and why 74% is used for the adaptive icon.
