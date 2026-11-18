#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )); then
    printf 'Usage: %s [new-key-directory]\n' "$0" >&2
    exit 2
fi

output=${1:-keys}
umask 077
mkdir -- "$output"
trap 'rm -f -- "$output/private-key" "$output/public-key"; rmdir -- "$output"' EXIT
trap 'exit 1' HUP INT TERM

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 |
    openssl pkcs8 -topk8 -nocrypt -outform DER |
    openssl base64 -A > "$output/private-key"

openssl base64 -d -A -in "$output/private-key" |
    openssl pkey -inform DER -pubout -outform DER |
    openssl base64 -A > "$output/public-key"

trap - EXIT HUP INT TERM
printf 'Generated JWT keys in %s\n' "$output"
printf 'Configure JWT_PRIVATE_KEY_FILE and JWT_PUBLIC_KEY_FILE with the paths to private-key and public-key.\n'
