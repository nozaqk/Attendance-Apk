#!/usr/bin/env bash
# Builds the single Smart Attendance APK: one native app (Kotlin + Jetpack
# Compose) with the Lecturer portal and the Student portal inside it.
# No Node.js, no web build, no WebView.
#
# Prerequisites:
#   * JDK 17
#   * Android SDK with platform 34 + build-tools 34.0.0 (ANDROID_HOME / ANDROID_SDK_ROOT)
# Usage: ./scripts/build-apk.sh
#        API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path." >&2
  exit 1
fi

echo "==> Building native APK"
cd "$ROOT/android"
chmod +x gradlew
# Point the app at your custom domain with: API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh
if [[ -n "${API_BASE_URL:-}" ]]; then
  echo "    API base URL: $API_BASE_URL"
  ./gradlew assembleDebug "-PAPI_BASE_URL=$API_BASE_URL"
else
  echo "    API base URL: default (Render host) — set API_BASE_URL to use your domain"
  ./gradlew assembleDebug
fi

echo
echo "APK: $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
echo "Install with: adb install -r $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
