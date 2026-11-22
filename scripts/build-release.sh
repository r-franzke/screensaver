#!/usr/bin/env bash
# Builds the signed release APK for version $1 (called by semantic-release in CI before tagging;
# if this fails, no tag and no release are created).
set -euo pipefail

version="${1:?usage: build-release.sh <version>}"

missing=""
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then missing="$missing $name"; fi
done
if [ -n "$missing" ]; then
  echo "::error title=Signing secrets missing::Cannot build the signed release $version. Missing repository secrets:$missing. Add them under Settings > Secrets and variables > Actions, then re-run this workflow."
  exit 1
fi

keystore_dir="${RUNNER_TEMP:-/tmp}"
mkdir -p "$keystore_dir"
keystore="$keystore_dir/release.jks"
trap 'rm -f "$keystore"' EXIT
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore"

SIGNING_KEYSTORE_PATH="$keystore" VERSION_NAME="$version" ./gradlew --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk web-screensaver.apk

apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner"
"$apksigner" verify web-screensaver.apk # fails the release if the signature is not valid
"$apksigner" verify --print-certs web-screensaver.apk | grep -E "certificate (DN|SHA-256)" || true
