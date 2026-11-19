#!/usr/bin/env bash
set -euo pipefail

# Pinned Android ARM64 Python package built by the YTDLnis packages project.
# This package bundles CPython 3.14.8 and curl_cffi 0.17.0.
PYTHON_APK_URL="https://github.com/deniscerri/ytdlnis-packages/releases/download/python-3.14.8/app-python-arm64-v8a-release.apk"
PYTHON_APK_SHA256="073430d0db34333a4a9d6b3e6801c3a00212f1a9632e05182c18165ab1ee1ca4"
YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp"
YTDLP_SHA256="e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSETS="$ROOT/app/src/main/assets"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$DEST" "$ASSETS"

curl -fL --retry 5 --retry-delay 2 "$PYTHON_APK_URL" -o "$TMP/python-runtime.apk"
echo "$PYTHON_APK_SHA256  $TMP/python-runtime.apk" | sha256sum -c -

for lib in libpython.so libpython.zip.so libqjs.so; do
  unzip -p "$TMP/python-runtime.apk" "lib/arm64-v8a/$lib" > "$DEST/$lib"
  test -s "$DEST/$lib"
done
unzip -l "$DEST/libpython.zip.so" | grep -E 'site-packages/curl_cffi/__init__\.py' >/dev/null

curl -fL --retry 5 --retry-delay 2 "$YTDLP_URL" -o "$ASSETS/yt-dlp"
echo "$YTDLP_SHA256  $ASSETS/yt-dlp" | sha256sum -c -
test -s "$ASSETS/yt-dlp"

echo "Prepared verified ARM64 runtime: Python 3.14.8 / curl_cffi 0.17.0 / yt-dlp 2026.10.26"
