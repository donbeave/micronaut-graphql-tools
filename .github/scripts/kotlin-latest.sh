#!/usr/bin/env bash
# Moves the Kotlin and KSP plugin pins in build.gradle.kts to the newest stable releases, for the canary's working tree only.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

newest() { # group artifact
  curl -fsSL --retry 3 "https://repo1.maven.org/maven2/${1//.//}/$2/maven-metadata.xml" |
    grep -oE '<version>[0-9]+(\.[0-9]+)+</version>' | sed -E 's:</?version>::g' | sort -V | tail -1
}

kotlin=$(newest org.jetbrains.kotlin kotlin-gradle-plugin)
ksp=$(newest com.google.devtools.ksp symbol-processing-gradle-plugin)
[ -n "$kotlin" ] && [ -n "$ksp" ] || { echo "no newest Kotlin or KSP release found" >&2; exit 1; }
# BSD sed reads `-i -E` as a backup suffix, so the edit goes through a temporary file.
sed -E "s/(id\(\"org.jetbrains.kotlin.jvm\"\) version )\"[^\"]+\"/\1\"$kotlin\"/; s/(id\(\"com.google.devtools.ksp\"\) version )\"[^\"]+\"/\1\"$ksp\"/" build.gradle.kts > build.gradle.kts.new
mv build.gradle.kts.new build.gradle.kts
# A pin that did not move would test the old Kotlin under the new name: fail instead.
if ! grep -qF "id(\"org.jetbrains.kotlin.jvm\") version \"$kotlin\"" build.gradle.kts ||
  ! grep -qF "id(\"com.google.devtools.ksp\") version \"$ksp\"" build.gradle.kts; then
  echo "could not move the Kotlin and KSP pins in build.gradle.kts" >&2
  exit 1
fi
git diff --stat build.gradle.kts
echo "Kotlin $kotlin, KSP $ksp" | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
