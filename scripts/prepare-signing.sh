#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${SIGNING_KEY:?Set the SIGNING_KEY repository secret}"
: "${ORG_GRADLE_PROJECT_releaseStorePassword:?Set the KEY_STORE_PASSWORD repository secret}"
: "${ORG_GRADLE_PROJECT_releaseKeyAlias:?Set the ALIAS repository secret}"
: "${ORG_GRADLE_PROJECT_releaseKeyPassword:?Set the KEY_PASSWORD repository secret}"

key=$(printf "%s" "$SIGNING_KEY" | tr -d "[:space:]")
target="$RUNNER_TEMP/module-signing.jks"

if [ -z "$key" ]; then
    echo "SIGNING_KEY contains no base64 data" >&2
    exit 1
fi

umask 077
if ! printf "%s" "$key" | base64 --decode > "$target"; then
    rm -f "$target"
    echo "SIGNING_KEY is not valid base64" >&2
    exit 1
fi

if [ ! -s "$target" ]; then
    rm -f "$target"
    echo "SIGNING_KEY decoded to an empty file" >&2
    exit 1
fi
