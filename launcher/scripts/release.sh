#!/usr/bin/env bash
# Builds the installer for the version in package.json and publishes it to the public
# Vortexyyy/astro-releases repo, where installed launchers pick it up as an auto-update.
# Usage (from launcher/): bump "version" in package.json, then: bash scripts/release.sh
set -euo pipefail
cd "$(dirname "$0")/.."
GH="${GH:-gh}"
command -v "$GH" >/dev/null || GH="/c/Program Files/GitHub CLI/gh.exe"
VERSION=$(node -p "require('./package.json').version")
REPO=Vortexyyy/astro-releases
EXE="dist/Astro-Setup-$VERSION.exe"

if "$GH" release view "v$VERSION" --repo "$REPO" >/dev/null 2>&1; then
  echo "v$VERSION is already released - bump the version in package.json first." >&2
  exit 1
fi

npm run build
# electron-builder can't tag a commit from this (private) repo inside the releases repo,
# so it only builds here and the GitHub CLI creates the release.
DRIVE=X:
subst "$DRIVE" "$(cygpath -w "$PWD")" # the NSIS build fails on long Windows paths
trap 'subst "$DRIVE" //D' EXIT
(cd /x && npx electron-builder --win nsis --publish never)

SIZE=$(stat -c %s "$EXE")
SHA=$(openssl dgst -sha512 -binary "$EXE" | base64 -w0)
cat > dist/latest.yml <<YML
version: $VERSION
files:
  - url: Astro-Setup-$VERSION.exe
    sha512: $SHA
    size: $SIZE
path: Astro-Setup-$VERSION.exe
sha512: $SHA
releaseDate: '$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'
YML

"$GH" release create "v$VERSION" "$EXE" "$EXE.blockmap" dist/latest.yml \
  --repo "$REPO" --title "Astro Launcher $VERSION" --notes "Installed launchers update to this automatically."
echo "Released v$VERSION: https://github.com/$REPO/releases/tag/v$VERSION"
