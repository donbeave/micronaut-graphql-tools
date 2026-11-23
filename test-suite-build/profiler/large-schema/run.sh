#!/usr/bin/env bash
# Measures graphql-java inside javac, KSP and the Groovy compiler on a synthetic schema of 2,000 types and 250 operation documents
# (#665). Each run is one clean build of a tiny project with the probe on the processor path of its compiler, in a fresh
# Gradle JVM (--no-daemon) of its own, so the schema build and the first validation pass are cold. Usage: run.sh [runs] [output-file]
# The versions are read from gradle.properties; overriding them on the command line is rejected, so the header is exact.
# Run with the JDK of the build (mise exec -- ./run.sh).
set -euo pipefail

runs="${1:-5}"
here="$(cd "$(dirname "$0")" && pwd)"
output="${2:-$here/timings.txt}"
root="$(cd "$here/../../.." && pwd)"
raw="$(mktemp)"
export LARGE_SCHEMA_OUT="$raw"
version() { sed -n "s/^$1=//p" "$here/gradle.properties"; }
graphql_java="$(version graphqlJavaVersion)"
kotlin="$(version kotlinVersion)"
ksp="$(version kspVersion)"
groovy="$(version groovyVersion)"
extra=("${@:3}")
for arg in ${extra[@]+"${extra[@]}"}; do
  case "$arg" in
    *graphqlJavaVersion*|*kotlinVersion*|*kspVersion*|*groovyVersion*)
      echo "run.sh: the versions come from gradle.properties; remove the override: $arg" >&2
      exit 1 ;;
  esac
done
gradle=("$root/gradlew" -p "$here" --no-daemon --no-build-cache -Pkotlin.compiler.execution.strategy=in-process ${extra[@]+"${extra[@]}"})

load_before="$(uptime | sed 's/.*load averages*: //')"
for ((i = 0; i < runs; i++)); do
  for task in :javac:compileJava :ksp:kspKotlin :groovy:compileGroovy; do
    rm -rf "$here/$(echo "$task" | cut -d: -f2)/build"
    lines="$(wc -l <"$raw")"
    "${gradle[@]}" "$task" --rerun >"$here/build-log.txt" 2>&1 || { cat "$here/build-log.txt"; exit 1; }
    if [ "$(wc -l <"$raw")" -eq "$lines" ]; then
      echo "run $i of $task wrote no timing line; see $here/build-log.txt" >&2
      exit 1
    fi
  done
done
load_after="$(uptime | sed 's/.*load averages*: //')"

python3 -I - "$raw" "$output" "$graphql_java" "$kotlin" "$ksp" "$groovy" "$load_before" "$load_after" "$runs" <<'PY'
import statistics, subprocess, sys
raw, output, graphql_java, kotlin, ksp, groovy, before, after, runs = sys.argv[1:10]
rows = {}
for line in open(raw):
    fields = dict(item.split("=", 1) for item in line.split())
    rows.setdefault(fields["host"], []).append(fields)
metrics = ["schema-parse-ms", "schema-build-ms", "cold-doc-parse-ms", "cold-validate-ms", "warm-doc-parse-ms", "warm-validate-ms"]
java = subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()[0]
with open(output, "w") as out:
    out.write("# Large-schema timings (#665): median of %s clean builds per compiler, all values in milliseconds.\n" % runs)
    out.write("# command: mise exec -- test-suite-build/profiler/large-schema/run.sh %s\n" % runs)
    out.write("# JDK: %s\n# graphql-java: %s, Gradle: see ./gradlew --version, Kotlin %s, KSP %s, Groovy %s\n" % (java, graphql_java, kotlin, ksp, groovy))
    out.write("# load average before/after: %s / %s\n" % (before, after))
    out.write("# workload: %s types, %s documents, %s validation errors\n" % (rows["javac"][0]["types"], rows["javac"][0]["documents"], rows["javac"][0]["errors"]))
    out.write("# budgets (#665): cold schema build (schema-parse + schema-build) at most 2000 ms; warm validation of 250 documents (warm-validate) at most 100 ms\n")
    out.write("# schema-build = SchemaParser.parse (schema-parse) + UnExecutableSchemaGenerator (schema-build); cold = first call in the compiler JVM;\n")
    out.write("# warm = median of 30 passes after the cold pass in the same JVM; validate = Validator over all documents; doc-parse = Parser over all documents\n")
    out.write("%-8s %s\n" % ("compiler", " ".join("%18s" % m for m in metrics)))
    for host in ("javac", "ksp", "groovy"):
        values = [statistics.median(float(r[m]) for r in rows[host]) for m in metrics]
        out.write("%-8s %s\n" % (host, " ".join("%18.1f" % v for v in values)))
    out.write("\n# worst run of the schema build (schema-parse + schema-build), the cold schema build figure judged against the 2000 ms budget\n")
    for host in ("javac", "ksp", "groovy"):
        out.write("%-8s %18.1f\n" % (host, max(float(r["schema-parse-ms"]) + float(r["schema-build-ms"]) for r in rows[host])))
    out.write("\n# every run\n")
    for host in ("javac", "ksp", "groovy"):
        for r in rows[host]:
            out.write(" ".join("%s=%s" % (k, r[k]) for k in ["host"] + metrics) + "\n")
print(open(output).read())
PY
rm -f "$raw"
