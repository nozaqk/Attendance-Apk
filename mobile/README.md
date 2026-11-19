# SmartIQ — Flutter (Dart) app

This is the app rebuilt in **Dart/Flutter**: the same product, the same design and
the same behaviour as the Kotlin/Compose build in `../android`, written in Dart.
One APK, both portals (Lecturer and Student), same backend.

```
mobile/
├── lib/                     the whole app: screens, state, API, models, BLE logic, PDF content
│   ├── main.dart            launch, role picker, session gates
│   ├── config.dart          API_BASE_URL from --dart-define
│   ├── core/                API client, models, formatting, polling widget, token store
│   ├── state/               session per portal, class-alert helpers
│   ├── ble/                 Bluetooth verification controller + panel
│   ├── platform/native.dart the only bridge to Android (see below)
│   └── ui/                  theme, shared widgets, auth, lecturer/*, student/*
├── test/                    unit tests (models, formatting, API client)
└── android/                 Android host: MainActivity (bridge), service, receiver, PDF writer
```

## Design parity

The palette, spacing, radii, typography and every screen are identical to the
Kotlin build and the web app (`src/shared/styles.css`): navy `#12263F`, app
background `#F3F5F8`, card white with a `#DDE3EA` hairline, 12–14 radii, badges
with the same status→tone map, the same emoji tab bar, the same copy.

Feature parity:

| Feature | Where |
| --- | --- |
| Role picker, per-portal logins, separate sessions | `ui/role_picker.dart`, `main.dart` |
| Student self-registration (departments, stream) | `ui/auth/auth_screens.dart` |
| Forgot password (6-digit code) and forced password change | `ui/auth/auth_screens.dart` |
| Lecturer: class results, roster, manual attendance | `ui/lecturer/` |
| Lecturer: choose course + room, verify presence, start/end class | `ui/lecturer/lecturer_class.dart` |
| Lecturer: students, courses, settings | `ui/lecturer/` |
| Student: dashboard (today/week), live class, alerts, courses, profile | `ui/student/` |
| Bluetooth attendance verification (real sightings only) | `ble/`, `platform/native.dart` |
| Class alerts with the app closed, after reboot, tap opens the feed | `state/alerts.dart`, `android/.../NotificationService.kt` |
| Activity export to a PDF in Downloads | `ui/student/student_class.dart`, `android/.../PdfWriter.kt` |

## Why there is a small Kotlin bridge

Everything the user sees and every business rule is Dart. Three things are
Android OS APIs that no Dart code can call directly, so `MainActivity.kt` exposes
them over one method channel (`com.smartiq.app/native`) and one event channel
(`com.smartiq.app/ble`):

1. **BLE scanning** — the host reports raw sightings `{nodeId, rssi, timestamp}`;
   Dart throttles per node, posts `/api/attendance/ble-observation` and renders
   the verdict.
2. **Closed-app alert poller** — a foreground service polls
   `/api/student/notifications/feed` every 30 s with the restricted device token
   and posts notifications. It must run without the Flutter engine, which is why
   its loop lives in the service; Dart starts/stops it and owns the token.
3. **PDF → Downloads** — `MediaStore` + the framework PDF canvas. All content
   (period, rows, summary, filename) is computed in Dart.

Everything else — including the tap-to-open-feed behaviour — is Dart.

## Build

```bash
cd mobile
flutter pub get

# debug APK against the Render host
flutter build apk --debug

# point at a custom domain (one flag, no code changes)
flutter build apk --release --dart-define=API_BASE_URL=https://your-domain
```

CI: `.github/workflows/flutter-apk.yml` runs `pub get`, `flutter analyze`,
`flutter test` and `flutter build apk`, then uploads the APK artifact. Set the
repository **variable** `API_BASE_URL` (Settings → Secrets and variables →
Actions → Variables) to build against another origin.

Locally the Gradle wrapper needs the Flutter SDK path, which the `flutter` tool
writes into `android/local.properties` automatically — no manual step.

## Alerts

Unchanged from the Kotlin build: students must allow notifications (Android 13+
prompt), the poller is a foreground service (Android requires a visible
notification), it restarts after a reboot or app update via `BootReceiver`, and
aggressive OEM battery managers can still stop it. Signing out stops the poller,
forgets the device token and revokes it with `DELETE /api/student/notifications/feed`.

Verify on a device:

```bash
adb shell dumpsys activity services com.smartiq.app | grep NotificationService
```

## Differences worth knowing

* The release build is still signed with the debug key — the `KEYSTORE_*` secrets
  are deliberately not wired up yet.
* `flutter_secure_storage` is not used: tokens live in `SharedPreferences` under
  `lecturer_token` / `student_token`, matching the previous build.
* The UI does not adapt to a dark theme, exactly like the web app and the Kotlin
  build (the launch screen is forced light at night too).
