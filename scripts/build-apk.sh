#!/usr/bin/env bash
# Builds the single Smart Attendance APK (Lecturer + Student in one app).
# Prerequisites: Node.js 20+, JDK 17, Android SDK (platform 34 + build-tools 34.0.0).
# Usage: ./scripts/build-apk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Building lecturer web app"
(cd "$ROOT" && npm ci && npm run build)

echo "==> Building student web app"
(cd "$ROOT/student-frontend" && npm ci && npm run build)

echo "==> Copying web builds into Android assets"
mkdir -p "$ROOT/android/app/src/main/assets/lecturer" "$ROOT/android/app/src/main/assets/student"
cp -r "$ROOT/dist/"* "$ROOT/android/app/src/main/assets/lecturer/"
cp -r "$ROOT/student-frontend/dist/"* "$ROOT/android/app/src/main/assets/student/"

echo "==> Building APK"
(cd "$ROOT/android" && ./gradlew assembleDebug)

echo "APK: $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
