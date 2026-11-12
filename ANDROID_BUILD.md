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

- App name: **Smart Attendance**
- Package ID: `com.smartattendance.app`
- Minimum Android: 8.0 (API 26); target/compile SDK 34
- Backend: `https://attendance-system-33pl.onrender.com` (baked into
  `data/Api.kt`, matching the web `.env.production`)

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
3. Open the finished run → **Artifacts** → download `smart-attendance-apk`
   (contains `app-debug.apk`).

Debug APKs are signed with the standard debug key and install directly on
any Android 8.0+ phone.

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
3. Open **Smart Attendance**, choose **Lecturer** or **Student**, and sign
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

## Release signing

Debug APKs are for installation and testing. For a production release:

1. Generate a keystore: `keytool -genkey -v -keystore release.jks -alias attendance -keyalg RSA -keysize 2048 -validity 10000`
2. Add a `signingConfigs.release` block in `app/build.gradle.kts` reading
   the keystore path/passwords from environment variables or
   `local.properties` (never commit the keystore or passwords).
3. Run `./gradlew assembleRelease`.

## Troubleshooting

- **"Bluetooth verification needs the Smart Attendance Android app"**:
  that message only appears in a mobile browser. Use the installed app.
- **Node never detected**: check Bluetooth is on, permissions are granted,
  and (Android 11 and older) Location is on. The ESP32 node must be
  powered and advertising nearby.
- **No background alerts (student)**: allow notifications, and exempt the
  app from battery optimisation in system settings.
- **Gradle can't resolve dependencies offline**: the first build needs
  internet access to `dl.google.com` / Maven Central.
