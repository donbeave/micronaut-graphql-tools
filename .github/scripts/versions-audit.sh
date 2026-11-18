#!/usr/bin/env bash
# Compares every pin with the newest supported release (D-34) and exits 1 when one is behind. The supported limits are in the calls below.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

CENTRAL=https://repo1.maven.org/maven2
PORTAL=https://plugins.gradle.org/m2
CATALOG=gradle/libs.versions.toml
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/null}
behind=0
failed=0

printf '| Pin | Pinned | Newest supported | Limit | Status |\n| --- | --- | --- | --- | --- |\n' | tee -a "$SUMMARY"

row() { printf '| %s | %s | %s | %s | %s |\n' "$@" | tee -a "$SUMMARY"; }

# Highest of the stable versions (digits and dots only) on stdin; sort -V orders them numerically.
newest_of() { grep -E "$1" | sort -V | tail -1; }

versions_of() { # base group artifact
  curl -fsSL --retry 3 "$1/${2//.//}/$3/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | sed -E 's:</?version>::g'
}

# compare label pinned newest limit: records the row, counts it behind when newest > pinned
compare() {
  local label=$1 pinned=$2 newest=$3 limit=$4 status=ok
  if [ -z "$newest" ] || [ -z "$pinned" ]; then
    status="unknown"; failed=1
    echo "::error title=versions audit::$label: could not resolve the pinned ('$pinned') or the newest ('$newest') version."
  elif [ "$pinned" != "$newest" ] && [ "$(printf '%s\n%s\n' "$pinned" "$newest" | sort -V | tail -1)" = "$newest" ]; then
    status="BEHIND"; behind=$((behind + 1))
    echo "::error title=versions audit::$label is behind: pinned $pinned, newest supported $newest ($limit)."
  fi
  row "$label" "$pinned" "$newest" "$limit" "$status"
}

catalog() { sed -n "s/^$1 = \"\\(.*\\)\"/\\1/p" "$CATALOG"; }
plugin_pin() { grep -rhoE "id\(\"$1\"\) version \"[^\"]+\"" --include=*.kts . | sed -E 's/.*version "([^"]+)"/\1/' | head -1; }

# newest <base> <group> <artifact> <ERE for the supported stable versions>
newest() { versions_of "$1" "$2" "$3" | newest_of "$4"; }

# Micronaut 4 lines only: Micronaut 5 is not supported (D-14), so it is never looked at.
compare "micronaut (core BOM)" "$(catalog micronaut)" "$(newest $CENTRAL io.micronaut micronaut-core-bom '^4\.[0-9.]+$')" "4.x"
compare "micronaut-platform" "$(catalog micronaut-platform)" "$(newest $CENTRAL io.micronaut.platform micronaut-platform '^4\.[0-9.]+$')" "4.x"
compare "micronaut-test" "$(catalog micronaut-test)" "$(newest $CENTRAL io.micronaut.test micronaut-test-bom '^4\.[0-9.]+$')" "4.x"
compare "micronaut-graphql" "$(catalog micronaut-graphql)" "$(newest $CENTRAL io.micronaut.graphql micronaut-graphql-bom '^4\.[0-9.]+$')" "4.x"
compare "micronaut-docs" "$(catalog micronaut-docs)" "$(newest $CENTRAL io.micronaut.docs micronaut-docs-asciidoc-config-props '^[0-9.]+$')" "newest"
compare "io.micronaut.build.shared.settings" "$(sed -n 's/.*io.micronaut.build.shared.settings") version "\(.*\)"/\1/p' settings.gradle.kts)" \
  "$(newest $PORTAL io.micronaut.build.shared.settings io.micronaut.build.shared.settings.gradle.plugin '^7\.[0-9.]+$')" "7.x (8.x is Micronaut 5)"
compare "io.micronaut.application" "$(plugin_pin io.micronaut.application)" \
  "$(newest $PORTAL io.micronaut.application io.micronaut.application.gradle.plugin '^4\.[0-9.]+$')" "4.x (5.x is Micronaut 5)"
compare "io.micronaut.aot" "$(plugin_pin io.micronaut.aot)" \
  "$(newest $PORTAL io.micronaut.aot io.micronaut.aot.gradle.plugin '^4\.[0-9.]+$')" "4.x (5.x is Micronaut 5)"

# Kotlin and KSP follow the newest Micronaut 4 core BOM; the newest Kotlin runs in the canary.
core_bom=$(newest $CENTRAL io.micronaut micronaut-core-bom '^4\.[0-9.]+$')
bom_pom=$(curl -fsSL --retry 3 "$CENTRAL/io/micronaut/micronaut-core-bom/$core_bom/micronaut-core-bom-$core_bom.pom")
compare "kotlin" "$(plugin_pin org.jetbrains.kotlin.jvm)" "$(sed -n 's:.*<kotlin2.version>\(.*\)</kotlin2.version>.*:\1:p' <<<"$bom_pom")" "core BOM $core_bom kotlin2.version"
compare "ksp" "$(plugin_pin com.google.devtools.ksp)" "$(sed -n 's:.*<ksp2.version>\(.*\)</ksp2.version>.*:\1:p' <<<"$bom_pom")" "core BOM $core_bom ksp2.version"

# Exception (D-41): the only pin of a Micronaut-managed version. The BOM manages 4.0.28, which cannot read JDK 27 class files; the pin stays until the BOM catches up.
compare "groovy (override of the BOM)" "$(catalog groovy)" "$(newest $CENTRAL org.apache.groovy groovy '^4\.0\.[0-9]+$')" "4.0.x (the Micronaut 4 line)"
compare "shadow" "$(plugin_pin com.gradleup.shadow)" "$(newest $PORTAL com.gradleup.shadow com.gradleup.shadow.gradle.plugin '^8\.[0-9.]+$')" "8.x (the line micronaut-gradle-plugin builds against)"

# Micronaut-managed dependencies (logback, JUnit, graphql-java and its extended scalars through micronaut-graphql, Spock, ...) have no
# pin here: they come from the Micronaut BOMs and move with micronaut-platform above (D-41).

# Outside Micronaut's control: the newest stable release.
compare "apollo" "$(catalog apollo)" "$(newest $CENTRAL com.apollographql.apollo apollo-runtime '^[0-9.]+$')" "newest"
compare "apollo plugin" "$(plugin_pin com.apollographql.apollo)" "$(newest $CENTRAL com.apollographql.apollo apollo-runtime '^[0-9.]+$')" "newest"
compare "apollo-java client" "$(catalog apollo-java)" "$(newest $CENTRAL com.apollographql.java client '^[0-9.]+$')" "newest"
compare "apollo-java rx3" "$(catalog apollo-java)" "$(newest $CENTRAL com.apollographql.java rx3 '^[0-9.]+$')" "newest"
compare "jmh" "$(catalog jmh)" "$(newest $CENTRAL org.openjdk.jmh jmh-core '^[0-9.]+$')" "newest"
compare "jmh plugin" "$(plugin_pin me.champeau.jmh)" "$(newest $PORTAL me.champeau.jmh me.champeau.jmh.gradle.plugin '^[0-9.]+$')" "newest"
compare "jetbrains-annotations" "$(catalog jetbrains-annotations)" "$(newest $CENTRAL org.jetbrains annotations '^[0-9.]+$')" "newest"
compare "graphql-java-datetime" "$(catalog graphql-java-datetime)" "$(newest $CENTRAL com.tailrocks.graphql graphql-java-datetime '^[0-9.]+$')" "newest"

# graphql-java legs: each pin is the newest release of its line (floor 21.x, default 22.x, ceiling 25.x until GJ26).
line_major() { printf '^%s\\.[0-9.]+$' "${1%%.*}"; }
line_minor() { local m=${1%.*}; printf '^%s\\.[0-9]+$' "${m//./\\.}"; }
leg() { # catalog-key group artifact (major|minor)
  local pin line
  pin=$(catalog "$1")
  if [ "$4" = major ]; then line=$(line_major "$pin"); else line=$(line_minor "$pin"); fi
  if [ "$4" = major ]; then limit="${pin%%.*}.x"; else limit="${pin%.*}.x"; fi
  compare "$1" "$pin" "$(newest $CENTRAL "$2" "$3" "$line")" "line $limit"
}
leg floor-graphql-java com.graphql-java graphql-java major
leg floor-extended-scalars com.graphql-java graphql-java-extended-scalars major
leg floor-java-dataloader com.graphql-java java-dataloader minor
leg floor-extended-validation com.graphql-java graphql-java-extended-validation major
leg ceiling-graphql-java com.graphql-java graphql-java major
leg ceiling-extended-scalars com.graphql-java graphql-java-extended-scalars major
leg ceiling-java-dataloader com.graphql-java java-dataloader minor
leg ceiling-extended-validation com.graphql-java graphql-java-extended-validation major
leg graphql-java-extended-validation com.graphql-java graphql-java-extended-validation major
row "graphql-java (newest, canary)" "-" "$(newest $CENTRAL com.graphql-java graphql-java '^[0-9]+\.[0-9]+$')" "info: exercised by the latest leg" "info"

compare "gradle wrapper" "$(sed -n 's/.*gradle-\(.*\)-bin.zip/\1/p' gradle/wrapper/gradle-wrapper.properties)" "$(curl -fsSL --retry 3 https://services.gradle.org/versions/current | jq -r .version)" "current"

# mise: tools and JDKs. Supported JDK lines are 17, 21 and 25 (LTS); the canary line is the newest GA JDK.
mise_pin() { sed -n "s/^$1 = \"\\(.*\\)\"/\\1/p" "$2"; }
compare "jactionlint" "$(sed -n 's|^"github:jdx/jactionlint" = "\(.*\)"|\1|p' mise.toml)" "$(gh api repos/jdx/jactionlint/releases/latest --jq .tag_name | sed 's/^v//')" "newest"
graalvm=$(mise_pin java mise.toml)
compare "java (mise.toml)" "$graalvm" "$(mise ls-remote java oracle-graalvm-25 | grep -E '^oracle-graalvm-25\.[0-9.]+$' | sort -V | tail -1)" "Oracle GraalVM 25.x"
for jdk in 17 21 25 27; do
  pin=$(mise_pin java "mise.jdk$jdk.toml")
  latest=$(curl -fsSL --retry 3 "https://api.adoptium.net/v3/assets/latest/$jdk/hotspot?image_type=jdk&os=linux&architecture=x64" | jq -r '.[0].release_name' | sed 's/^jdk-//')
  latest=$(cut -d. -f1-3 <<<"${latest%%+*}")  # Adoptium respins carry extra components (27.0.1.0.2); the build number is not compared
  pin_base=${pin#temurin-}; pin_base=${pin_base%.0.LTS}; pin_base=${pin_base%.LTS}
  compare "java (mise.jdk$jdk.toml)" "${pin_base%%+*}" "$latest" "Temurin $jdk"
done
ga=$(curl -fsSL --retry 3 https://api.adoptium.net/v3/info/available_releases | jq -r .most_recent_feature_release)
compare "newest GA JDK (the canary line)" "27" "$ga" "the canary overlay tracks the newest GA JDK"

# GitHub Actions: the version comment next to every full-SHA pin against the newest release of the action.
grep -rhoE 'uses: [^ ]+@[0-9a-f]{40} +# +v?[0-9][^ ]*' .github | sed -E 's/uses: ([^@]+)@[0-9a-f]{40} +# +v?(.*)/\1 \2/' | sort -u |
while read -r action pinned; do
  repo=$(cut -d/ -f1,2 <<<"$action")
  latest=$(gh api "repos/$repo/releases/latest" --jq .tag_name | sed 's/^v//')
  echo "$action $pinned $latest"
done > "${RUNNER_TEMP:-/tmp}/actions-pins.txt"
while read -r action pinned latest; do
  compare "action $action" "$pinned" "$latest" "newest release"
done < "${RUNNER_TEMP:-/tmp}/actions-pins.txt"

if [ "$behind" -gt 0 ] || [ "$failed" -gt 0 ]; then
  echo "Versions audit: $behind pin(s) behind, unresolved lookups: $failed." | tee -a "$SUMMARY" >&2
  exit 1
fi
echo "Versions audit: every pin is at the newest supported release." | tee -a "$SUMMARY"
