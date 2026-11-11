# Android APKs (Lecturer + Student)

This repo contains two web apps and two native Android shells:

| App | Web source | Android project | Package ID | Launcher name |
| --- | --- | --- | --- | --- |
| Lecturer | repo root (`src/`) | `android-lecturer/` | `com.smartattendance.lecturer` | Smart Attendance Lecturer |
| Student | `student-frontend/` | `android-student/` | `com.smartattendance.student` | Smart Attendance Student |

**The web design is unchanged.** No web source file was modified to make the
APKs. Each Android shell is a thin native container that loads the exact
Vite production build (`dist/`) from its bundled assets and implements only
the native bridges the web code already expects:

- `window.AndroidBle` — real Bluetooth LE scanning for classroom ESP32 nodes
  (`src/shared/ble.tsx`). Measurements are never simulated.
- `window.AndroidNotify` — local notifications, background notification
  polling (student), battery-optimisation exemption, and saving exported
  PDFs to Downloads (`src/shared/native.ts`, `src/exportActivity.ts`).

The shells use only Android framework APIs (WebView, BluetoothLeScanner,
NotificationManager, MediaStore) — no AndroidX or third-party dependencies.

## Get the APKs (no local Android setup needed)

APKs are built by GitHub Actions (`.github/workflows/build-apk.yml`):

1. Open the repo on GitHub → **Actions** → **Build APKs** → **Run workflow**.
2. Wait for the run to finish (a few minutes).
3. Open the finished run → **Artifacts** → download `lecturer-apk` and/or
   `student-apk` (each contains `app-debug.apk`).

Debug APKs are signed with the standard debug key and can be installed
directly on any Android 8.0+ (API 26+) phone. For Play Store / production
distribution, create a release keystore and configure release signing in
`app/build.gradle.kts` (see "Release signing" below).

## Build locally

Prerequisites: Node.js 20+, JDK 17, Android SDK with platform 34 and
build-tools 34.0.0 (Android Studio installs these automatically).

```bash
# Both APKs
./scripts/build-apk.sh all
# Or just one
./scripts/build-apk.sh lecturer
./scripts/build-apk.sh student
```

Outputs:

- `android-lecturer/app/build/outputs/apk/debug/app-debug.apk`
- `android-student/app/build/outputs/apk/debug/app-debug.apk`

Or open `android-lecturer/` (or `android-student/`) in Android Studio and
press **Run**. Before the first Gradle build, the web build must be copied
into `app/src/main/assets/` — the script above does this; manually it is:

```bash
npm run build && cp -r dist/* android-lecturer/app/src/main/assets/
```

## Install on a phone

1. Copy the APK to the phone (USB, Drive, etc.) and open it.
2. Allow "Install unknown apps" for the browser/file manager if prompted.
3. Open the app and sign in with the same accounts as the web portals.

## Permissions (requested by the app)

| Permission | Why |
| --- | --- |
| Bluetooth scan/connect (Android 12+) or Location (older) | Find the classroom ESP32 node for attendance verification |
| Notifications (Android 13+) | "Class started / attendance finalised" alerts |
| Battery-optimisation exemption (student, optional) | Keep background class alerts working when the app is closed |

On Android 11 and older, Location must be turned **on** in system settings
for Bluetooth scanning to work (Android requirement, not app behaviour).

## How it works

- The WebView serves bundled assets at `https://appassets.androidplatform.net/`
  so the app keeps its exact design, client-side routing and `localStorage`
  login session. The backend URL is baked in at web-build time from
  `.env.production` (`VITE_API_BASE_URL`), exactly as for the web deploys.
- Bluetooth sightings are reported to the page as
  `window.onBleMeasurement({ nodeId, rssi, timestamp })`; the page sends
  them to `POST /api/attendance/ble-observation` as before.
- The student background service polls `GET /api/student/notifications`
  with the restricted device token from
  `POST /api/student/notifications/device-token` every 30 seconds and shows
  a notification for each new unread item. It starts on sign-in and stops
  on sign-out. It never uses the user's login token.
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

- **Blank screen**: the web build was not copied into assets. Re-run the
  build script or copy `dist/*` into `app/src/main/assets/` and rebuild.
- **"Bluetooth verification needs the Smart Attendance Android app"**:
  you are in a mobile browser, not the APK. Open the installed app instead.
- **Node never detected**: check Bluetooth is on, permissions are granted,
  and (Android 11 and older) Location is on. The ESP32 node must be
  powered and advertising nearby.
- **No background alerts (student)**: allow notifications, and exempt the
  app from battery optimisation in system settings.
