#!/usr/bin/env bash
set -euo pipefail
# Requires Android platform-tools on the host and USB debugging on the real phone.
APK=${1:?Usage: device_smoke.sh app-debug.apk app-debug-androidTest.apk output-directory}
TEST_APK=${2:?Missing test APK}
RESULT_DIR=${3:?Missing output directory}
mkdir -p "$RESULT_DIR"
adb get-state
adb shell getprop ro.product.model > "$RESULT_DIR/device-model.txt"
adb shell getprop ro.build.fingerprint > "$RESULT_DIR/device-build.txt"
adb shell cat /proc/meminfo > "$RESULT_DIR/device-memory.txt"
adb install -r "$APK"
adb install -r "$TEST_APK"
adb shell dumpsys package org.offlineatlas.preview > "$RESULT_DIR/package.txt"
if rg -q 'android.permission.INTERNET' "$RESULT_DIR/package.txt"; then
  echo 'Unexpected Internet permission' >&2
  exit 1
fi
adb shell am instrument -w -e class org.offlineatlas.OfflineSmokeTest org.offlineatlas.preview.test/android.test.InstrumentationTestRunner > "$RESULT_DIR/smoke.txt"
cat "$RESULT_DIR/smoke.txt"
if ! rg -q 'OK \(2 tests\)' "$RESULT_DIR/smoke.txt"; then exit 1; fi
adb shell dumpsys meminfo org.offlineatlas.preview > "$RESULT_DIR/launch-memory.txt"
echo 'Smoke tests passed. Inference, disconnected use, latency, peak RAM, and whole-answer quality still need the device protocol.'
