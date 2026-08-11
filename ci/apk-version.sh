#!/usr/bin/env bash
# Read the versionCode/versionName that are actually compiled into an APK, so
# the published manifest can't drift from the artifact. Run under `nix develop`
# for aapt2 from the pinned SDK.
#
# Usage: ci/apk-version.sh <apk> <out-env>
#   writes `versionCode=…` / `versionName=…` lines to <out-env> (for $GITHUB_OUTPUT).
set -euo pipefail

apk="${1:?usage: apk-version.sh <apk> <out-env>}"
out="${2:?usage: apk-version.sh <apk> <out-env>}"
aapt2="${ANDROID_HOME:?ANDROID_HOME not set}/build-tools/35.0.0/aapt2"

line="$("$aapt2" dump badging "$apk" | grep '^package:')"
version_code="$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$line")"
version_name="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$line")"

[ -n "$version_code" ] || { echo "could not read versionCode from $apk" >&2; exit 1; }
[ -n "$version_name" ] || { echo "could not read versionName from $apk" >&2; exit 1; }

{
  echo "versionCode=$version_code"
  echo "versionName=$version_name"
} > "$out"
