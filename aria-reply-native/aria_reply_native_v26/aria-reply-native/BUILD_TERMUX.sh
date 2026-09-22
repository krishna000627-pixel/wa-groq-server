#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug --no-daemon
else
  echo "Gradle is not installed. Use GitHub Actions, or install Gradle in Termux first."
  exit 1
fi
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
