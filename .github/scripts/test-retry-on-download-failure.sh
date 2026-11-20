#!/usr/bin/env bash
# Stub test of retry-on-download-failure.sh: no network, no Gradle. Run by the actionlint job.
set -uo pipefail
script="$(cd "$(dirname "$0")" && pwd)/retry-on-download-failure.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export RETRY_WAIT_SECONDS=0
failures=0

# stub <name> <first-attempt output> <first-attempt exit code>: fails once with the output, then succeeds.
stub() {
  cat > "$work/$1" <<STUB
#!/usr/bin/env bash
count=\$(cat "$work/$1.count" 2>/dev/null || echo 0)
echo \$((count + 1)) > "$work/$1.count"
if [ "\$count" -eq 0 ]; then printf '%s\n' "$2"; exit $3; fi
echo recovered
STUB
  chmod +x "$work/$1"
}

# expect <name> <expected exit code> <expected runs> <expected output substring>
expect() {
  local out status runs
  out=$("$script" "$work/$1" 2>&1 | tee "$work/$1.out")
  status=${PIPESTATUS[0]}
  runs=$(cat "$work/$1.count")
  if [ "$status" -ne "$2" ] || [ "$runs" -ne "$3" ] || ! grep -qF "$4" <<<"$out"; then
    echo "FAIL $1: exit $status (want $2), runs $runs (want $3), output: $out"
    failures=$((failures + 1))
  else
    echo "ok   $1"
  fi
}

stub download "Could not GET 'https://repo.maven.apache.org/x'. Received status code 429" 3
expect download 0 2 recovered
stub plain "boom" 5
expect plain 5 1 boom
stub transport "java.io.IOException: Connection reset" 4
expect transport 4 1 "Connection reset"
stub mixed "Could not GET 'x'.
There were failing tests" 1
expect mixed 1 1 "failing tests"
stub failedtask "Execution failed for task ':test-suite-java:test'.
> Could not resolve com.example:x:1" 1
expect failedtask 1 1 "Execution failed"
cat > "$work/always" <<STUB
#!/usr/bin/env bash
echo \$(( \$(cat "$work/always.count" 2>/dev/null || echo 0) + 1 )) > "$work/always.count"
echo "Could not resolve x"; exit 9
STUB
chmod +x "$work/always"
expect always 9 3 "Could not resolve"
exit "$failures"
