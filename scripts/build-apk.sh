#!/usr/bin/env bash
# Builds the single Smart Attendance APK: one native app (Kotlin + Jetpack
# Compose) with the Lecturer portal and the Student portal inside it.
# No Node.js, no web build, no WebView.
#
# Prerequisites:
#   * JDK 17
#   * Android SDK with platform 34 + build-tools 34.0.0 (ANDROID_HOME / ANDROID_SDK_ROOT)
#
# Usage:
#   ./scripts/build-apk.sh                                  # debug APK
#   BUILD_TYPE=release ./scripts/build-apk.sh               # release APK (debug-signed until a keystore is configured)
#   API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path." >&2
  exit 1
fi

BUILD_TYPE="${BUILD_TYPE:-debug}"
case "$BUILD_TYPE" in
  debug)   TASK="assembleDebug" ;;
  release) TASK="assembleRelease" ;;
  *) echo "BUILD_TYPE must be 'debug' or 'release' (got '$BUILD_TYPE')" >&2; exit 1 ;;
esac

echo "==> Building native APK ($BUILD_TYPE)"
cd "$ROOT/android"
chmod +x gradlew

GRADLE_ARGS=()
# Point the app at your custom domain with: API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh
if [[ -n "${API_BASE_URL:-}" ]]; then
  echo "    API base URL: $API_BASE_URL"
  GRADLE_ARGS+=("-PAPI_BASE_URL=$API_BASE_URL")
else
  echo "    API base URL: default (Render host) — set API_BASE_URL to use your domain"
fi

if [[ "$BUILD_TYPE" == "release" && -z "${KEYSTORE_FILE:-}" ]]; then
  echo "    Signing: no keystore configured — Gradle will sign with the DEBUG key."
  echo "             Fine for testing; set KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS /"
  echo "             KEY_PASSWORD (or android/local.properties) before distributing."
fi

./gradlew "$TASK" "${GRADLE_ARGS[@]}"

echo
if [[ "$BUILD_TYPE" == "release" ]]; then
  APK_DIR="$ROOT/android/app/build/outputs/apk/release"
  echo "APK(s) in: $APK_DIR"
  ls -1 "$APK_DIR"/*.apk 2>/dev/null || true
  echo "Install with: adb install -r $APK_DIR/app-release.apk"
else
  echo "APK: $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
  echo "Install with: adb install -r $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
fi
