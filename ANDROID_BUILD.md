# Android app build

The current SmartIQ app is the Flutter/Dart application in [`mobile/`](mobile/README.md).
Use the project build script from the repository root:

```bash
./scripts/build-apk.sh
BUILD_TYPE=release API_BASE_URL=https://your-domain ./scripts/build-apk.sh
```

The Flutter APK is produced at `mobile/build/app/outputs/flutter-apk/`.
See the [mobile README](mobile/README.md) for all prerequisites, features, CI
checks, and Android platform integration details.

The top-level `android/` directory contains the previous Kotlin/Compose
implementation and is not the active product build. The Flutter Android host is
in `mobile/android/`; its Kotlin classes are limited to Android-specific platform
adapters documented in the mobile README.
