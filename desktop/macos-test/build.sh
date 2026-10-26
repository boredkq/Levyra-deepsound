#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${SCRIPT_DIR}/Levyra.app"
CONTENTS_DIR="${APP_DIR}/Contents"
MACOS_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
TMP_DIR="$(mktemp -d /tmp/levyra_universal_build.XXXXXX)"

trap 'rm -rf "${TMP_DIR}"' EXIT

echo "==> Cleaning previous build artifacts..."
rm -rf "${APP_DIR}" "${OUTPUT_DIR}"
mkdir -p "${MACOS_DIR}" "${RESOURCES_DIR}" "${OUTPUT_DIR}"

echo "==> Compiling arm64 slice..."
swiftc -O -target "arm64-apple-macos11.0" \
  "${SCRIPT_DIR}/main.swift" \
  -o "${TMP_DIR}/Levyra_arm64"

echo "==> Compiling x86_64 slice..."
swiftc -O -target "x86_64-apple-macos11.0" \
  "${SCRIPT_DIR}/main.swift" \
  -o "${TMP_DIR}/Levyra_x86_64"

echo "==> Creating Universal binary with lipo..."
lipo -create \
  "${TMP_DIR}/Levyra_arm64" \
  "${TMP_DIR}/Levyra_x86_64" \
  -output "${MACOS_DIR}/Levyra"

chmod +x "${MACOS_DIR}/Levyra"
file "${MACOS_DIR}/Levyra"

echo "==> Copying resources and Info.plist..."
cp "${SCRIPT_DIR}/Info.plist" "${CONTENTS_DIR}/Info.plist"
cp "${SCRIPT_DIR}/index.html" "${RESOURCES_DIR}/index.html"

# If icon exists, copy it
if [ -f "${SCRIPT_DIR}/AppIcon.icns" ]; then
  cp "${SCRIPT_DIR}/AppIcon.icns" "${RESOURCES_DIR}/AppIcon.icns"
fi

echo "==> Applying ad-hoc code signature..."
codesign --force --deep -s - "${APP_DIR}"

echo "==> Packaging portable Universal zip..."
(cd "${SCRIPT_DIR}" && zip -r -q "${OUTPUT_DIR}/LEVYRA-macOS-AAA-Test-Universal.zip" "Levyra.app")

echo "==> Creating macOS DMG installer image..."
DMG_STAGING="${TMP_DIR}/dmg_staging"
mkdir -p "${DMG_STAGING}"
cp -R "${APP_DIR}" "${DMG_STAGING}/"
ln -s /Applications "${DMG_STAGING}/Applications"

hdiutil create -volname "Levyra Desktop" \
  -srcfolder "${DMG_STAGING}" \
  -ov -format UDZO \
  "${OUTPUT_DIR}/LEVYRA-macOS-AAA-Test-Universal.dmg"

echo "==> Generating SHA256 checksums..."
cd "${OUTPUT_DIR}"
for f in *; do
  if [ -f "$f" ]; then
    shasum -a 256 "$f" > "$f.sha256"
  fi
done

echo "==> Universal build complete! Artifacts in ${OUTPUT_DIR}:"
ls -la "${OUTPUT_DIR}"
