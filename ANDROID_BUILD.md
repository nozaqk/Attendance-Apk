# Android APK (Lecturer + Student in one app)

This repo contains two web apps and one native Android shell:

| Web app | Source | Bundled in APK as |
| --- | --- | --- |
| Lecturer | repo root (`src/`) | `assets/lecturer/` |
| Student | `student-frontend/` | `assets/student/` |

**One APK, two roles.** On launch the app shows a role picker
(*Lecturer* / *Student*). Tapping a role opens that portal — sign in or
create an account as usual — and each portal then operates completely
separately:

- Separate logins and sessions (each portal has its own storage; signing
  into one never signs into the other).
- Separate screens and navigation (each portal keeps its own tabs and
  pages, exactly as on the web).
- Press **Back** at a portal's home to return to the role picker and
  switch roles.

**The web design is unchanged.** No web source file was modified. The
native shell loads the exact Vite production builds from bundled assets
and implements only the native bridges the web code already expects:

- `window.AndroidBle` — real Bluetooth LE scanning for classroom ESP32 nodes
  (`src/shared/ble.tsx`). Measurements are never simulated.
- `window.AndroidNotify` — local notifications, background notification
  polling (student only), battery-optimisation exemption, and saving
  exported PDFs to Downloads (`src/shared/native.ts`, `src/exportActivity.ts`).

The shell uses only Android framework APIs (WebView, BluetoothLeScanner,
NotificationManager, MediaStore) — no AndroidX or third-party dependencies.

- App name: **Smart Attendance**
- Package ID: `com.smartattendance.app`
- Minimum Android: 8.0 (API 26)

## Get the APK (no local Android setup needed)

The APK is built by GitHub Actions (`.github/workflows/build-apk.yml`):

1. Open the repo on GitHub → **Actions** → **Build APK** → **Run workflow**
   (or open a pull request — the workflow runs automatically).
2. Wait for the run to finish (a few minutes).
3. Open the finished run → **Artifacts** → download `smart-attendance-apk`
   (contains `app-debug.apk`).

Debug APKs are signed with the standard debug key and can be installed
directly on any Android 8.0+ phone. For Play Store / production
distribution, create a release keystore and configure release signing in
`app/build.gradle.kts` (see "Release signing" below).

## Build locally

Prerequisites: Node.js 20+, JDK 17, Android SDK with platform 34 and
build-tools 34.0.0 (Android Studio installs these automatically).

```bash
./scripts/build-apk.sh
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Or open `android/` in Android Studio and press **Run**. Before the first
Gradle build, both web builds must be copied into assets — the script
above does this; manually it is:

```bash
npm run build && cp -r dist/* android/app/src/main/assets/lecturer/
(cd student-frontend && npm run build) && cp -r student-frontend/dist/* android/app/src/main/assets/student/
```

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

## How it works

- The WebView serves bundled assets from two origins so the portals stay
  separate:
  `https://lecturer.appassets.androidplatform.net/` (lecturer) and
  `https://student.appassets.androidplatform.net/` (student). Each keeps
  its exact design, client-side routing and `localStorage` login session.
  The backend URL is baked in at web-build time from `.env.production`
  (`VITE_API_BASE_URL`), exactly as for the web deploys.
- Bluetooth sightings are reported to the page as
  `window.onBleMeasurement({ nodeId, rssi, timestamp })`; the page sends
  them to `POST /api/attendance/ble-observation` as before.
- The student background service polls `GET /api/student/notifications`
  with the restricted device token from
  `POST /api/student/notifications/device-token` every 30 seconds and shows
  a notification for each new unread item. It starts on student sign-in
  and stops on sign-out. It never uses the user's login token, and the
  lecturer portal never starts it.
- Exported PDFs (student "Export my activity") are saved to the phone's
  **Downloads** folder via the `saveFile` bridge.

## Release signing

Debug APKs are for installation and testing. For a production release:

1. Generate a keystore: `keytool -genkey -v -keystore release.jks -alias attendance -keyalg RSA -keysize 2048 -validity 10000`
2. Add a `signingConfigs.release` block in `app/build.gradle.kts` reading
   the keystore path/passwords from environment variables or
   `local.properties` (never commit the keystore or passwords).
3. Run `./gradlew assembleRelease`.

## Troubleshooting

- **Blank screen**: the web builds were not copied into assets. Re-run the
  build script or copy both `dist/` folders into
  `app/src/main/assets/lecturer/` and `app/src/main/assets/student/`,
  then rebuild.
- **"Bluetooth verification needs the Smart Attendance Android app"**:
  you are in a mobile browser, not the APK. Open the installed app instead.
- **Node never detected**: check Bluetooth is on, permissions are granted,
  and (Android 11 and older) Location is on. The ESP32 node must be
  powered and advertising nearby.
- **No background alerts (student)**: allow notifications, and exempt the
  app from battery optimisation in system settings.
