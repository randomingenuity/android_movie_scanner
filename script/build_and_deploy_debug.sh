#!/usr/bin/env bash

# Build the debug APK and install it on the first connected Android device.
# Usage:
#   ./script/build_and_deploy_debug.sh

set -euo pipefail

SCRIPT_DIRECTORY_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT_PATH="$(cd "${SCRIPT_DIRECTORY_PATH}/.." && pwd)"
DEBUG_APK_FILEPATH="${REPOSITORY_ROOT_PATH}/app/build/outputs/apk/debug/app-debug.apk"

"${SCRIPT_DIRECTORY_PATH}/build_debug.sh" "$@"

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found on PATH" >&2
  exit 1
fi

mapfile -t DEVICE_SERIALS < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if [ "${#DEVICE_SERIALS[@]}" -eq 0 ]; then
  echo "error: no Android device in 'device' state (check adb devices)" >&2
  exit 1
fi

DEVICE_SERIAL="${DEVICE_SERIALS[0]}"

echo "Installing debug APK on device ${DEVICE_SERIAL}." >&2
adb -s "${DEVICE_SERIAL}" install -r "${DEBUG_APK_FILEPATH}"
