#!/usr/bin/env bash
# Runs a command and runs it again, after a wait, when it failed on a transient dependency download problem (HTTP 429 or 5xx,
# a timeout, a dropped connection). Gradle retries a 429 itself, but 3 times within a quarter of a second, which does not outlast
# a rate limit; this wrapper waits a minute or more. Any other failure (a test, a compile error, a dependency that does not
# exist) ends the run at once, and the exit code is always that of the last attempt, so a real failure is never masked.
# The retries are bounded: at most 3 attempts, and none once RETRY_BUDGET_SECONDS (default 900) have passed since the start,
# so a long build that fails late is not run again. Usage: retry-on-download-failure.sh <command> [args...]
set -o pipefail
attempts=3
budget=${RETRY_BUDGET_SECONDS:-900}
log=$(mktemp)
trap 'rm -f "$log"' EXIT
start=$SECONDS
for attempt in $(seq 1 "$attempts"); do
  "$@" 2>&1 | tee -a "$log"
  status=$?
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  wait=$((60 * attempt))
  if [ "$attempt" -eq "$attempts" ] || [ $((SECONDS - start + wait)) -ge "$budget" ] || ! grep -qE \
    'Received status code (429|50[0-9])|Too Many Requests|Could not (GET|HEAD|transfer|download|resolve) |Read timed out|Connect(ion)? timed out|Connection reset|Remote host terminated|Premature end of Content-Length' "$log"; then
    exit "$status"
  fi
  echo "::warning title=dependency download failed::Attempt $attempt of $attempts failed on a download; retrying in ${wait}s."
  : > "$log"
  sleep "$wait"
done
