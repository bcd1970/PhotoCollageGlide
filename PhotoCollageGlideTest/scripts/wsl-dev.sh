#!/usr/bin/env bash
set -euo pipefail

# WSL developer helper for build/install/run using Windows SDK/ADB

APP_ID="com.photocollage.glide.test"
MAIN_ACTIVITY="com.photocollage.glide.MainActivity"

# Override via env if needed
WIN_SDK_DIR=${ANDROID_SDK_WINDOWS_PATH:-"/mnt/c/Users/cozmita/AppData/Local/Android/Sdk"}
ADB_EXE="$WIN_SDK_DIR/platform-tools/adb.exe"

if [[ ! -x "$ADB_EXE" && ! -f "$ADB_EXE" ]]; then
  echo "error: adb.exe not found at: $ADB_EXE" >&2
  echo "Set ANDROID_SDK_WINDOWS_PATH to your Windows SDK path under /mnt/c." >&2
  exit 1
fi

run_gradle() {
  # Use Windows Gradle wrapper to ensure Windows SDK compatibility
  /mnt/c/Windows/System32/cmd.exe /C gradlew.bat "$@"
}

adb() {
  "$ADB_EXE" "$@"
}

usage() {
  cat <<EOF
Usage: scripts/wsl-dev.sh [command]

Commands:
  devices         List connected devices
  uninstall       Uninstall app from device
  build           Assemble debug APK
  install         Install debug APK to device
  run             Launch main activity
  all             Uninstall, installDebug, then launch

Env:
  ANDROID_SDK_WINDOWS_PATH  Override Windows SDK path (default: $WIN_SDK_DIR)
EOF
}

cmd=${1:-all}
case "$cmd" in
  devices)
    adb devices
    ;;
  uninstall)
    adb uninstall "$APP_ID" || true
    ;;
  build)
    run_gradle :app:assembleDebug --console=plain
    ;;
  install)
    run_gradle :app:installDebug --console=plain
    ;;
  run)
    adb shell am start -n "$APP_ID/$MAIN_ACTIVITY"
    ;;
  all)
    adb devices
    adb uninstall "$APP_ID" || true
    run_gradle :app:installDebug --console=plain
    adb shell am start -n "$APP_ID/$MAIN_ACTIVITY"
    ;;
  *)
    usage
    exit 2
    ;;
esac

