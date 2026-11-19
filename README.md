# SmartIQ Attendance

SmartIQ is an attendance app for lecturers and students. The active mobile app is
built with **Flutter and Dart** in [`mobile/`](mobile/README.md). It ships as one
Android APK containing both portals; the app's screens, navigation, API client,
validation, models, and business logic are Dart.

The Android host includes a small Kotlin platform adapter for Android-only
capabilities (Bluetooth LE scanning, background alert service, and saving PDFs
into Downloads). Those APIs cannot be provided by Dart code alone; all product
UI and app logic remain in Dart. Shell scripts are used for local build
automation.

## Build the Android app

Requirements: Flutter 3.27.x, JDK 17, Android SDK, and `adb` for installation.

```bash
./scripts/build-apk.sh
```

To build a release variant or use another backend URL:

```bash
BUILD_TYPE=release API_BASE_URL=https://your-domain ./scripts/build-apk.sh
```

APK output: `mobile/build/app/outputs/flutter-apk/app-debug.apk` (or
`app-release.apk`). You can also build directly:

```bash
cd mobile
flutter pub get
flutter analyze
flutter test
flutter build apk --release --dart-define=API_BASE_URL=https://your-domain
```

The build and validation run in GitHub Actions for changes to `mobile/`; the
latest successful build on `main` is published as `smartiq-latest.apk`.

## App capabilities

- Separate lecturer and student portals and sessions
- Student registration, password reset, and forced password changes
- Lecturer class setup, live attendance verification, results, roster and
  manual attendance corrections
- Student dashboard, course list, attendance history, notifications and PDF
  activity export
- Bluetooth attendance verification and class alerts

See [`mobile/README.md`](mobile/README.md) for the app structure, feature map,
platform bridge details, and full build instructions.
