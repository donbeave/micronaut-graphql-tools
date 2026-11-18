#!/usr/bin/env python3
"""Start-up report (#39): binding time and retained heap per schema size, as medians of cold JVMs.

Run from anywhere. For each schema size it builds a JMH jar that generates that size only (-PlargeFields), then runs
both start-up benchmarks on it, one fresh JVM per fork. The last jar built stays in benchmarks/build/libs, so run
`./gradlew :benchmarks:jmhJar` again before the other benchmarks:

  python3 benchmarks/scripts/startup-report.py                       run, print the medians
  python3 benchmarks/scripts/startup-report.py --json run.json       also keep the JMH JSON in run.json
  python3 benchmarks/scripts/startup-report.py --from run.json --previous old.json
                                                                     medians of run.json and the change against old.json

The median is taken over the per-fork values in the JMH JSON (rawData). The JSON is the only input of the report, so
--from and --previous work on any run archived with `-rf json`. `retainedBytes` is a secondary result: JMH's text
summary adds the forks up, so it is not used here.
"""

import argparse
import json
import statistics
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BENCHMARKS = "BindingBenchmark|RetainedHeapBenchmark"
STARTUP_CLASSES = ("BindingBenchmark", "RetainedHeapBenchmark")
SIZES = (100, 1600, 3000, 10000, 20000)
MB = 1e6


def per_fork(metric):
    """One value per fork: single shot with one iteration, so each fork's rawData holds one number."""
    return [statistics.fmean(fork) for fork in metric["rawData"]]


def cases(results):
    """(fields, manifest recorded) -> {"ms": per-fork binding time, "bytes": per-fork retained heap}."""
    table = {}
    for result in results:
        cls = result["benchmark"].split(".")[-2]
        if cls not in STARTUP_CLASSES:
            continue
        key = (int(result["params"]["fields"]), result["params"]["record"] == "true")
        entry = table.setdefault(key, {"ms": [], "bytes": []})
        if cls == "BindingBenchmark":
            entry["ms"] = per_fork(result["primaryMetric"])
        elif cls == "RetainedHeapBenchmark":
            entry["bytes"] = per_fork(result["secondaryMetrics"]["retainedBytes"])
    return table


def label(key):
    fields, record = key
    return f"fields={fields:,} manifest={'on' if record else 'off'}"


def print_medians(results, out):
    first = results[0]
    out.write(f"JDK {first['jdkVersion']} ({first['vmName']}), {first['forks']} forks per case\n")
    out.write(f"{'case':<46}{'median ms':>11}{'min ms':>10}{'max ms':>10}{'median retained MB':>21}\n")
    for key, entry in sorted(cases(results).items()):
        ms, retained = entry["ms"], entry["bytes"]
        out.write(f"{label(key):<46}{statistics.median(ms):>11.1f}{min(ms):>10.1f}{max(ms):>10.1f}"
                  f"{statistics.median(retained) / MB:>21.2f}\n")


def print_change(results, previous, out):
    now, before = cases(results), cases(previous)
    out.write("\nChange against the previous run (medians, new against old):\n")
    for key in sorted(now):
        if key not in before:
            out.write(f"  {label(key)}: not in the previous run\n")
            continue
        for name, unit, scale in (("ms", "ms", 1.0), ("bytes", "MB", MB)):
            new = statistics.median(now[key][name]) / scale
            old = statistics.median(before[key][name]) / scale
            out.write(f"  {label(key):<46} {name:<6}{old:>10.2f} -> {new:>10.2f} {unit} "
                      f"({new - old:+.2f} {unit}, {100.0 * (new - old) / old:+.1f}%)\n")


def run_jmh(json_path, forks):
    """One jar per schema size, so that the other sizes are not on the classpath of the JVM that measures a size."""
    results = []
    with tempfile.TemporaryDirectory(prefix="startup-report-") as tmp:
        for size in SIZES:
            # A jar of an earlier build (another project version or size) must not be taken for this one: remove them
            # first, so that the build recreates the jar, and take the newest below in any case.
            for stale in (ROOT / "benchmarks" / "build" / "libs").glob("*-jmh.jar"):
                stale.unlink()
            subprocess.run([str(ROOT / "gradlew"), ":benchmarks:jmhJar", f"-PlargeFields={size}", "--no-daemon"],
                           cwd=ROOT, check=True)
            jars = sorted((ROOT / "benchmarks" / "build" / "libs").glob("*-jmh.jar"), key=lambda p: p.stat().st_mtime)
            if not jars:
                sys.exit("no benchmark jar in benchmarks/build/libs")
            jar = Path(tmp) / f"jmh-{size}.jar"
            part = Path(tmp) / f"jmh-{size}.json"
            jar.write_bytes(jars[-1].read_bytes())
            # -Xss16m: the 10,000-field schema recurses once per type of its chain (see CONTRIBUTING.md and #297).
            subprocess.run(["java", "-jar", str(jar), BENCHMARKS, "-p", f"fields={size}", "-f", str(forks),
                            "-wi", "0", "-i", "1", "-bm", "ss", "-jvmArgs", "-Xss16m", "-rf", "json", "-rff", str(part)],
                           cwd=ROOT, check=True)
            results.extend(load(part))
        Path(json_path).write_text(json.dumps(results, indent=2), encoding="utf-8")


def load(path):
    with open(path, encoding="utf-8") as f:
        results = json.load(f)
    if not results:
        sys.exit(f"{path} has no results")
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--forks", type=int, default=10, help="cold JVMs per case (default 10)")
    parser.add_argument("--json", help="keep the JMH JSON in this file (default: a temporary file)")
    parser.add_argument("--from", dest="source", help="report this JMH JSON file instead of running the benchmarks")
    parser.add_argument("--previous", help="JMH JSON of an earlier run: print the change per case")
    args = parser.parse_args()

    if args.source:
        json_path = args.source
    else:
        json_path = args.json or str(Path(tempfile.mkdtemp(prefix="startup-report-")) / "jmh.json")
        run_jmh(json_path, args.forks)
        print(f"JMH JSON: {json_path}")
    results = load(json_path)
    print_medians(results, sys.stdout)
    if args.previous:
        print_change(results, load(args.previous), sys.stdout)


if __name__ == "__main__":
    main()
