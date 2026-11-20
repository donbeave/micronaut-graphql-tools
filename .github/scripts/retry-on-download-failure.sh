#!/usr/bin/env bash
# Runs a command and runs it again, after a wait, when it failed on a dependency download or resolution error. Gradle and Maven
# report every such failure (HTTP 429 or 5xx, a timeout, a dropped connection) as "Could not GET/HEAD/download/transfer/resolve",
# so only that message counts: a bare "Connection reset" or "Read timed out" is also what a failing test prints.
# Gradle retries a 429 itself, but 3 times within a quarter of a second, which does not outlast a rate limit; this wrapper
# waits a minute or more. Any other failure (a test, a compile error, a dependency that does not
# exist) ends the run at once, and the exit code is always that of the last attempt, so a real failure is never masked.
# A run that also failed a test or a check (--continue lets one run fail on both) is never run again: the rerun would hide the
# test failure behind a green retry, so the test failure ends the run at once.
# The retries are bounded: at most 3 attempts, and none once RETRY_BUDGET_SECONDS (default 900) have passed since the start,
# so a long build that fails late is not run again. Usage: retry-on-download-failure.sh <command> [args...]
set -o pipefail
attempts=3
budget=${RETRY_BUDGET_SECONDS:-900}
unit=${RETRY_WAIT_SECONDS:-60}
log=$(mktemp)
trap 'rm -f "$log"' EXIT
start=$SECONDS
for attempt in $(seq 1 "$attempts"); do
  "$@" 2>&1 | tee -a "$log"
  status=$?
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  wait=$((unit * attempt))
  if [ "$attempt" -eq "$attempts" ] || [ $((SECONDS - start + wait)) -ge "$budget" ] || grep -qE \
    'There were failing tests|Execution failed for task .*([Tt]est|check)|^.* > .* FAILED$|[0-9]+ tests completed, [0-9]+ failed' "$log" || ! grep -qE \
    'Could not (GET|HEAD|download|transfer|resolve) ' "$log"; then
    exit "$status"
  fi
  echo "::warning title=dependency download failed::Attempt $attempt of $attempts failed on a download; retrying in ${wait}s."
  : > "$log"
  sleep "$wait"
done
