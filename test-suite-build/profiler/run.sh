#!/usr/bin/env bash
# Runs the gradle-profiler scenarios of the compile-time checks (#418) against the prepared copies of the samples.
# Usage: run.sh <gradle-profiler> <projects-dir> <output-dir> <warmups> <iterations> <gradle-version>
set -euo pipefail

profiler="$1"
projects="$2"
output="$3"
warmups="$4"
iterations="$5"
gradle_version="$6"
gradle_user_home="$(dirname "$projects")/gradle-user-home"
scenarios="$(cd "$(dirname "$0")" && pwd)/compile-time.scenarios"

rm -rf "$output"
mkdir -p "$output"
for project in "$projects"/*/; do
  name="$(basename "$project")"
  mkdir -p "$output/$name"
  # the client samples edit their client document, the others their schema (the same rule as CompileTimeBudget.edits)
  case "$name" in *client*) edit=document ;; *) edit=schema ;; esac
  export COMPILE_TIME_LOG="$output/$name/compile-tasks.txt"
  "$profiler" --benchmark --project-dir "$project" --scenario-file "$scenarios" --output-dir "$output/$name" \
    --warmups "$warmups" --iterations "$iterations" --gradle-version "$gradle_version" --gradle-user-home "$gradle_user_home" \
    "$name-clean-on" "$name-clean-off" "$name-body-on" "$name-body-off" "$name-$edit-on" "$name-$edit-off"
done
