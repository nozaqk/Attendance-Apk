#!/usr/bin/env bash
# Build the SmartIQ Android app from its canonical Flutter/Dart project.
#
# Prerequisites: Flutter 3.27.x, JDK 17, and an Android SDK installed by Flutter.
# Usage:
#   ./scripts/build-apk.sh
#   BUILD_TYPE=release API_BASE_URL=https://your-domain ./scripts/build-apk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT/mobile"
BUILD_TYPE="${BUILD_TYPE:-debug}"

if ! command -v flutter >/dev/null 2>&1; then
  echo "Flutter is required. Install Flutter 3.27.x and add it to PATH." >&2
  exit 1
fi

case "$BUILD_TYPE" in
  debug|release) ;;
  *) echo "BUILD_TYPE must be 'debug' or 'release' (got '$BUILD_TYPE')" >&2; exit 1 ;;
esac

BUILD_ARGS=(build apk "--$BUILD_TYPE")
if [[ -n "${API_BASE_URL:-}" ]]; then
  echo "==> API base URL: $API_BASE_URL"
  BUILD_ARGS+=("--dart-define=API_BASE_URL=$API_BASE_URL")
else
  echo "==> API base URL: the configured default"
fi

if [[ "$BUILD_TYPE" == "release" ]]; then
  echo "==> Building Flutter release APK (debug signing is configured for testing)."
else
  echo "==> Building Flutter debug APK."
fi

cd "$PROJECT"
flutter pub get
flutter "${BUILD_ARGS[@]}"

APK="$PROJECT/build/app/outputs/flutter-apk/app-$BUILD_TYPE.apk"
echo ""
echo "APK: $APK"
echo "Install with: adb install -r \"$APK\""
