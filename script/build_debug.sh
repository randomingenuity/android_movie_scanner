#!/usr/bin/env bash

# Build the Movie Scanner debug APK via Gradle.
# Usage:
#   ./script/build_debug.sh

set -euo pipefail

SCRIPT_DIRECTORY_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT_PATH="$(cd "${SCRIPT_DIRECTORY_PATH}/.." && pwd)"

cd "${REPOSITORY_ROOT_PATH}"

./gradlew assembleDebug "$@"
