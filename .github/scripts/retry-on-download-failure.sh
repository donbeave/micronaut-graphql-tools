#!/usr/bin/env bash
# Runs a command and runs it again, after a wait, when it failed on a dependency download (HTTP 429 or 5xx, a timeout or a
# connection error, or an artifact that could not be resolved). Gradle 9.9.1 has no retry setting for 429, so CI does it here.
# Any other failure (a test, a compile error) ends the run at once. Usage: retry-on-download-failure.sh <command> [args...]
set -o pipefail
attempts=3
log=$(mktemp)
trap 'rm -f "$log"' EXIT
for attempt in $(seq 1 "$attempts"); do
  "$@" 2>&1 | tee -a "$log"
  status=$?
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  if [ "$attempt" -eq "$attempts" ] || ! grep -qE \
    'status code (429|50[0-9])|Too Many Requests|Could not (GET|HEAD|resolve|find|transfer|download)|Read timed out|Connect(ion)? timed out|Connection reset|Remote host terminated|Premature end of Content-Length' "$log"; then
    exit "$status"
  fi
  wait=$((60 * attempt))
  echo "::warning title=dependency download failed::Attempt $attempt of $attempts failed on a download; retrying in ${wait}s."
  : > "$log"
  sleep "$wait"
done
