#!/usr/bin/env bash
# Builds signed Amni-Drive release APK, packages the Amni-Drive Car Suite ZIP
# (Amni-Drive + Amni-Map Car Edition + Amni-Browse), uploads to Amnibro/Amni-Scient
# `site-downloads`, updates OTA feeds + site redirects/metadata, and prints next steps.
set -euo pipefail

DRIVE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SITE_DIR="${AMNI_SITE_DIR:-/home/anmire/amni-scient}"
MAP_DIR="${AMNI_MAP_DIR:-/home/anmire/ai/amni-map}"
GRADLE="${GRADLE_BIN:-/home/anmire/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle}"
GH="${GH_BIN:-$(command -v gh || echo /home/anmire/.local/bin/gh)}"
APKSIGNER="$(ls /home/anmire/Android/Sdk/build-tools/*/apksigner 2>/dev/null | tail -n 1)"

export ANDROID_HOME="${ANDROID_HOME:-/home/anmire/Android/Sdk}"

VER_NAME="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$DRIVE_DIR/app/build.gradle.kts")"
VER_CODE="$(grep -oP 'versionCode\s*=\s*\K\d+' "$DRIVE_DIR/app/build.gradle.kts")"
echo "=== Building signed Amni-Drive v${VER_NAME} (${VER_CODE}) ==="
(cd "$DRIVE_DIR" && "$GRADLE" -Dorg.gradle.java.home=/usr/lib/jvm/default :app:testDebugUnitTest :app:assembleRelease --no-daemon)

DRIVE_APK="$DRIVE_DIR/app/build/outputs/apk/release/app-release.apk"
[ -n "$APKSIGNER" ] && "$APKSIGNER" verify --print-certs "$DRIVE_APK" | head -n 6

MAP_APK="$MAP_DIR/joyride/build/outputs/apk/release/joyride-release.apk"
MAP_VER_NAME="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$MAP_DIR/joyride/build.gradle.kts")"
MAP_VER_CODE="$(grep -oP 'versionCode\s*=\s*\K\d+' "$MAP_DIR/joyride/build.gradle.kts")"
BROWSE_APK="$SITE_DIR/downloads/amni-browse.apk"

STAGE="$(mktemp -d /tmp/amni-drive-release.XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT

DRIVE_ASSET="Amni-Drive-v${VER_NAME}-${VER_CODE}.apk"
MAP_ASSET="Amni-Map-v${MAP_VER_NAME}-${MAP_VER_CODE}.apk"
SUITE_ASSET="Amni-Drive-Suite-v${VER_NAME}.zip"

cp "$DRIVE_APK" "$STAGE/$DRIVE_ASSET"
cp "$MAP_APK" "$STAGE/$MAP_ASSET"

mkdir -p "$STAGE/suite"
cp "$DRIVE_APK" "$STAGE/suite/01-${DRIVE_ASSET}"
cp "$MAP_APK" "$STAGE/suite/02-${MAP_ASSET}"
[ -f "$BROWSE_APK" ] && cp "$BROWSE_APK" "$STAGE/suite/03-Amni-Browse.apk"
cat > "$STAGE/suite/README-INSTALL.txt" <<EOF
Amni-Drive Car Suite v${VER_NAME}
https://amni-scient.com/amni-drive

Included signed Android APKs (Android 8.0+, no Google Play or ADB required):
  1. 01-${DRIVE_ASSET} — Universal USB Android Auto replacement + built-in OTA updater & Amni Apps Hub
  2. 02-${MAP_ASSET}   — Amni-Map with native car-tile live embed (DashInput) & offline maps
  3. 03-Amni-Browse.apk — 1 MB privacy-first browser

Install Steps:
  1. Install 01-${DRIVE_ASSET} and 02-${MAP_ASSET} on your Android phone (allow installs from this source if asked).
  2. Open Amni-Drive -> Settings -> OTA & AMNI APPS to install/update additional Amni apps anytime OTA.
  3. Open Amni-Drive -> Settings -> ADVANCED -> AMNI WIRE -> tap Android Auto App Info and Disable/Clear defaults so Amni-Drive claims your car USB port directly.
  4. Plug into your car's Android Auto USB port and tap "Always open Amni-Drive".

OTA Feeds:
  Amni-Drive: https://amni-scient.com/drive/android-latest.json
  Amni-Map:   https://amni-scient.com/map/android-latest.json
  Amni Suite: https://amni-scient.com/drive/apps-catalog.json
EOF

(cd "$STAGE/suite" && zip -q -r "$STAGE/$SUITE_ASSET" .)

DRIVE_SHA="$(sha256sum "$STAGE/$DRIVE_ASSET" | awk '{print $1}')"
DRIVE_SIZE="$(stat -c%s "$STAGE/$DRIVE_ASSET")"
MAP_SHA="$(sha256sum "$STAGE/$MAP_ASSET" | awk '{print $1}')"
MAP_SIZE="$(stat -c%s "$STAGE/$MAP_ASSET")"
SUITE_SHA="$(sha256sum "$STAGE/$SUITE_ASSET" | awk '{print $1}')"
SUITE_SIZE="$(stat -c%s "$STAGE/$SUITE_ASSET")"

echo "DRIVE: $DRIVE_ASSET size=$DRIVE_SIZE sha256=$DRIVE_SHA"
echo "MAP:   $MAP_ASSET size=$MAP_SIZE sha256=$MAP_SHA"
echo "SUITE: $SUITE_ASSET size=$SUITE_SIZE sha256=$SUITE_SHA"

if [ "${UPLOAD:-0}" = "1" ]; then
  echo "=== Uploading to Amnibro/Amni-Scient site-downloads ==="
  "$GH" release upload site-downloads \
    "$STAGE/$DRIVE_ASSET" \
    "$STAGE/$MAP_ASSET" \
    "$STAGE/$SUITE_ASSET" \
    --repo Amnibro/Amni-Scient --clobber
fi
