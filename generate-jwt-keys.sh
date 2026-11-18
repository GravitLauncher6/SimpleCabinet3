#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )); then
    printf 'Usage: %s [new-key-directory]\n' "$0" >&2
    exit 2
fi

output=${1:-keys}
umask 077
mkdir -- "$output"
trap 'rm -f -- "$output/private-key" "$output/public-key" "$output/.env"; rmdir -- "$output"' EXIT
trap 'exit 1' HUP INT TERM

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 |
    openssl pkcs8 -topk8 -nocrypt -outform DER |
    openssl base64 -A > "$output/private-key"

openssl base64 -d -A -in "$output/private-key" |
    openssl pkey -inform DER -pubout -outform DER |
    openssl base64 -A > "$output/public-key"

{
    printf 'JWT_PRIVATE_KEY=%s\n' "$(cat "$output/private-key")"
    printf 'JWT_PUBLIC_KEY=%s\n' "$(cat "$output/public-key")"
} > "$output/.env"

key_dir=$(cd -- "$output" && pwd -P)
trap - EXIT HUP INT TERM
printf 'Generated JWT keys in %s\n' "$output"
printf 'For standalone use, run these exports in the shell that starts the server and admin commands.\n'
printf 'export JWT_PRIVATE_KEY_FILE=%q\n' "$key_dir/private-key"
printf 'export JWT_PUBLIC_KEY_FILE=%q\n' "$key_dir/public-key"
printf 'For Docker Compose, pass --env-file "%s/.env" to both server and admin commands.\n' "$output"
