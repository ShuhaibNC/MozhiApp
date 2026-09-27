#!/bin/bash
# Mozhi APK build (no Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner
#
# Used both locally and by GitHub Actions (.github/workflows/build.yml).
#
# Env overrides:
#   ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK location
#   JAVA_HOME                        JDK 8 (defaults to java on PATH)
#   KEYSTORE                         debug keystore path (generated if missing)
#   OUT_APK                          output APK path
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP="$SCRIPT_DIR/app/src/main"

if [ -n "$ANDROID_HOME" ]; then
  SDK="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
  SDK="$ANDROID_SDK_ROOT"
else
  SDK="$HOME/workspace/.buildtools/android-sdk"
fi
BT_VER=29.0.3
PLATFORM=android-29
TOOLS="$SDK/build-tools/$BT_VER"

if [ -n "$JAVA_HOME" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# --- version (single source of truth: app/build.gradle literals) ---
VERSION_CODE=$(grep -oP 'versionCode\s+\K[0-9]+' "$SCRIPT_DIR/app/build.gradle" | head -1)
VERSION_NAME=$(grep -oP 'versionName\s+"\K[^"]+' "$SCRIPT_DIR/app/build.gradle" | head -1)
OUT_APK="${OUT_APK:-$SCRIPT_DIR/Mozhi-v$VERSION_NAME.apk}"

# --- dictionary DB: generated at build time from the vendored data/enml.json ---
# (the prebuilt binary is intentionally not committed; F-Droid builds it too)
if [ ! -s "$APP/assets/mozhi.db" ]; then
  echo "== generating mozhi.db from data/enml.json =="
  python3 "$SCRIPT_DIR/tools/mkdb.py" "$SCRIPT_DIR/data/enml.json" "$APP/assets/mozhi.db"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/compiled" "$WORK/gen" "$WORK/classes" "$WORK/dex"

echo "== Mozhi v$VERSION_NAME (versionCode $VERSION_CODE) =="

echo "== aapt2 compile =="
"$TOOLS/aapt2" compile --dir "$APP/res" -o "$WORK/compiled/res.zip"

echo "== aapt2 link =="
"$TOOLS/aapt2" link -o "$WORK/base.apk" \
  -I "$SDK/platforms/$PLATFORM/android.jar" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --min-sdk-version 21 --target-sdk-version 29 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" \
  "$WORK/compiled/res.zip"

echo "== javac =="
find "$APP/java" "$WORK/gen" -name "*.java" > "$WORK/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$SDK/platforms/$PLATFORM/android.jar" \
  -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

echo "== d8 =="
"$TOOLS/d8" --min-api 21 \
  --lib "$SDK/platforms/$PLATFORM/android.jar" \
  --output "$WORK/dex" \
  $(find "$WORK/classes" -name "*.class")

echo "== package =="
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK/dex" && zip -q -j "$WORK/unsigned.apk" classes.dex)

echo "== zipalign =="
"$TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

echo "== sign (debug key) =="
KEYSTORE="${KEYSTORE:-$HOME/.mozhi-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi
"$TOOLS/apksigner" sign --ks "$KEYSTORE" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUT_APK" "$WORK/aligned.apk"

echo "== verify =="
"$TOOLS/apksigner" verify --print-certs "$OUT_APK" | head -3
ls -la "$OUT_APK"
echo "BUILD OK: $OUT_APK"
