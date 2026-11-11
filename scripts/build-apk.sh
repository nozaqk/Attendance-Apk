#!/usr/bin/env bash
# Builds the Lecturer and/or Student APK locally.
# Prerequisites: Node.js 20+, JDK 17, Android SDK (platform 34 + build-tools 34.0.0).
# Usage: ./scripts/build-apk.sh [lecturer|student|all]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"

build_one() {
  local kind="$1"
  echo "==> Building $kind web app"
  if [ "$kind" = "lecturer" ]; then
    (cd "$ROOT" && npm ci && npm run build)
    echo "==> Copying lecturer web build into Android assets"
    mkdir -p "$ROOT/android-lecturer/app/src/main/assets"
    cp -r "$ROOT/dist/"* "$ROOT/android-lecturer/app/src/main/assets/"
    echo "==> Building lecturer APK"
    (cd "$ROOT/android-lecturer" && ./gradlew assembleDebug)
    echo "APK: $ROOT/android-lecturer/app/build/outputs/apk/debug/app-debug.apk"
  else
    (cd "$ROOT/student-frontend" && npm ci && npm run build)
    echo "==> Copying student web build into Android assets"
    mkdir -p "$ROOT/android-student/app/src/main/assets"
    cp -r "$ROOT/student-frontend/dist/"* "$ROOT/android-student/app/src/main/assets/"
    echo "==> Building student APK"
    (cd "$ROOT/android-student" && ./gradlew assembleDebug)
    echo "APK: $ROOT/android-student/app/build/outputs/apk/debug/app-debug.apk"
  fi
}

case "$TARGET" in
  lecturer) build_one lecturer ;;
  student) build_one student ;;
  all) build_one lecturer; build_one student ;;
  *) echo "Usage: $0 [lecturer|student|all]" >&2; exit 1 ;;
esac
