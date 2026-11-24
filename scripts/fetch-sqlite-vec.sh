#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${SQLITE_VEC_VERSION:-0.1.9}"
DEST="backend/lib/sqlite-vec"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

OS="$(uname -s)"
ARCH="$(uname -m)"
case "$OS" in
  MINGW*|MSYS*|CYGWIN*) PLATFORM="windows-x86_64" ;;
  Linux*)               PLATFORM="linux-x86_64" ;;
  Darwin*)
    if [ "$ARCH" = "arm64" ]; then PLATFORM="macos-aarch64"; else PLATFORM="macos-x86_64"; fi
    ;;
  *) echo "Unsupported platform: $OS $ARCH" >&2; exit 1 ;;
esac

ARCHIVE="sqlite-vec-${VERSION}-loadable-${PLATFORM}.tar.gz"
BASE="https://github.com/asg017/sqlite-vec/releases/download/v${VERSION}"

echo "Downloading ${ARCHIVE} ..."
curl -fsSL --retry 3 -o "$TMP/vec.tar.gz" "$BASE/$ARCHIVE"
curl -fsSL --retry 3 -o "$TMP/checksums.txt" "$BASE/checksums.txt"

if command -v sha256sum >/dev/null 2>&1; then
  EXPECTED="$(awk -v name="$ARCHIVE" '$1 == name {print $2}' "$TMP/checksums.txt")"
  ACTUAL="$(sha256sum "$TMP/vec.tar.gz" | awk '{print $1}')"
  if [ -n "$EXPECTED" ] && [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "Checksum mismatch for $ARCHIVE" >&2
    exit 1
  fi
fi

mkdir -p "$DEST"
tar -xzf "$TMP/vec.tar.gz" -C "$DEST"
echo "Installed sqlite-vec into $DEST"
