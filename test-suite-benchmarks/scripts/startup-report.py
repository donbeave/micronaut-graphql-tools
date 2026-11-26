#!/usr/bin/env python3
"""Start-up report (#39): binding time and retained heap per schema shape and size, as medians of cold JVMs.

Run from anywhere. For each schema shape and size it builds a JMH jar that generates that case only (-PlargeFields,
-PlargeShape), then runs both start-up benchmarks on it, one fresh JVM per fork. The last jar built stays in test-suite-benchmarks/build/libs, so run
`./gradlew :test-suite-benchmarks:jmhJar` again before the other benchmarks:

  python3 test-suite-benchmarks/scripts/startup-report.py                       run, print the medians
  python3 test-suite-benchmarks/scripts/startup-report.py --json run.json       also keep the JMH JSON in run.json
  python3 test-suite-benchmarks/scripts/startup-report.py --from run.json --previous old.json
                                                                     medians of run.json and the change against old.json
  python3 test-suite-benchmarks/scripts/startup-report.py --from run.json --baseline reference.json
                                                                     the same against another run's JSON
  python3 test-suite-benchmarks/scripts/startup-report.py --reference-root ../reference-checkout --reference-json reference.json
                                                                     build the reference checkout too, run both in turn, print the change

A start-up time depends on the machine: hosted runners of one kind differ by up to a factor of two from one run to the
next, so a time recorded on one machine says nothing about another. `--reference-root` takes a checkout of the reference
commit and builds its jar for every case beside the one of this checkout. Each round starts one cold JVM per build, in a
rotating order, so both builds run on the same machine seconds apart, and the change is the difference between the two
medians of this run, not against a stored figure. The reference results are kept in `--reference-json`.

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
# (shape, fields): the rings of up to 100 types at every size, and the interface-heavy schema (#368) at two
CASES = tuple(("rings", size) for size in (100, 1600, 3000, 10000, 20000)) + (("interfaces", 3000), ("interfaces", 20000))
MB = 1e6


def per_fork(metric):
    """One value per fork: single shot with one iteration, so each fork's rawData holds one number."""
    return [statistics.fmean(fork) for fork in metric["rawData"]]


def cases(results):
    """(shape, fields, manifest recorded) -> {"ms": per-fork binding time, "bytes": per-fork retained heap}."""
    table = {}
    for result in results:
        cls = result["benchmark"].split(".")[-2]
        if cls not in STARTUP_CLASSES:
            continue
        # a JSON archived before the interface-heavy schema has no shape parameter: it is the rings
        key = (result["params"].get("shape", "rings"), int(result["params"]["fields"]),
               result["params"]["record"] == "true")
        entry = table.setdefault(key, {"ms": [], "bytes": []})
        if cls == "BindingBenchmark":
            entry["ms"] = per_fork(result["primaryMetric"])
        elif cls == "RetainedHeapBenchmark":
            entry["bytes"] = per_fork(result["secondaryMetrics"]["retainedBytes"])
    return table


def label(key):
    shape, fields, record = key
    return f"{shape} fields={fields:,} manifest={'on' if record else 'off'}"


def print_medians(results, out):
    first = results[0]
    out.write(f"JDK {first['jdkVersion']} ({first['vmName']}), {first['forks']} forks per case\n")
    out.write(f"{'case':<56}{'median ms':>11}{'min ms':>10}{'max ms':>10}{'median retained MB':>21}\n")
    for key, entry in sorted(cases(results).items()):
        ms, retained = entry["ms"], entry["bytes"]
        out.write(f"{label(key):<56}{statistics.median(ms):>11.1f}{min(ms):>10.1f}{max(ms):>10.1f}"
                  f"{statistics.median(retained) / MB:>21.2f}\n")


def print_change(results, previous, out, title="the previous run"):
    now, before = cases(results), cases(previous)
    out.write(f"\nChange against {title} (medians, new against old):\n")
    for key in sorted(now):
        if key not in before:
            out.write(f"  {label(key)}: not in {title}\n")
            continue
        for name, unit, scale in (("ms", "ms", 1.0), ("bytes", "MB", MB)):
            new = statistics.median(now[key][name]) / scale
            old = statistics.median(before[key][name]) / scale
            out.write(f"  {label(key):<56} {name:<6}{old:>10.2f} -> {new:>10.2f} {unit} "
                      f"({new - old:+.2f} {unit}, {100.0 * (new - old) / old:+.1f}%)\n")


def build_jar(root, shape, size, target):
    """The jar of one schema shape and size of the checkout `root`, copied to `target` (the next build replaces it)."""
    libs = root / "test-suite-benchmarks" / "build" / "libs"
    # A jar of an earlier build (another project version or size) must not be taken for this one: remove them
    # first, so that the build recreates the jar, and take the newest below in any case.
    for stale in libs.glob("*-jmh.jar"):
        stale.unlink()
    subprocess.run([str(root / "gradlew"), ":test-suite-benchmarks:jmhJar", f"-PlargeFields={size}", f"-PlargeShape={shape}",
                    "--no-daemon"],
                   cwd=root, check=True)
    jars = sorted(libs.glob("*-jmh.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        sys.exit(f"no benchmark jar in {libs}")
    target.write_bytes(jars[-1].read_bytes())


def merge(parts):
    """One result per benchmark and parameters, with the forks of every part in rawData.

    The score is recomputed from the forks; the error, the interval and the percentiles of a single fork mean nothing for the merge, so they are dropped.
    The report reads rawData only."""
    merged = {}
    for result in parts:
        key = (result["benchmark"], json.dumps(result["params"], sort_keys=True))
        into = merged.setdefault(key, result)
        if into is not result:
            into["forks"] += result["forks"]
            into["primaryMetric"]["rawData"] += result["primaryMetric"]["rawData"]
            for name, metric in result.get("secondaryMetrics", {}).items():
                into["secondaryMetrics"][name]["rawData"] += metric["rawData"]
    for result in merged.values():
        for metric in (result["primaryMetric"], *result.get("secondaryMetrics", {}).values()):
            metric["score"] = statistics.fmean(per_fork(metric))
            for derived in ("scoreError", "scoreConfidence", "scorePercentiles"):
                metric.pop(derived, None)
    return list(merged.values())


def run_jmh(json_path, forks, cases_to_run, reference_root=None, reference_json=None):
    """One jar per schema shape and size, so that the other cases are not on the classpath of the JVM that measures one.

    With a reference checkout both builds start one cold JVM per round in a rotating order, so they share the machine."""
    roots = {"candidate": ROOT}
    if reference_root:
        roots["reference"] = reference_root
    names = list(roots)
    results = {name: [] for name in names}
    with tempfile.TemporaryDirectory(prefix="startup-report-") as tmp:
        for shape, size in cases_to_run:
            jars = {}
            for name, root in roots.items():
                jars[name] = Path(tmp) / f"jmh-{name}-{shape}-{size}.jar"
                build_jar(root, shape, size, jars[name])
            parts = {name: [] for name in names}
            for round_number in range(forks):
                shift = round_number % len(names)
                for name in names[shift:] + names[:shift]:
                    part = Path(tmp) / f"jmh-{name}-{shape}-{size}-{round_number}.json"
                    subprocess.run(["java", "-jar", str(jars[name]), BENCHMARKS, "-p", f"fields={size}", "-p", f"shape={shape}",
                                    "-f", "1", "-wi", "0", "-i", "1", "-bm", "ss", "-rf", "json", "-rff", str(part)],
                                   cwd=ROOT, check=True)
                    parts[name].extend(load(part))
            for name in names:
                results[name].extend(merge(parts[name]))
    Path(json_path).write_text(json.dumps(results["candidate"], indent=2), encoding="utf-8")
    if reference_root:
        Path(reference_json).write_text(json.dumps(results["reference"], indent=2), encoding="utf-8")


def load(path):
    with open(path, encoding="utf-8") as f:
        results = json.load(f)
    if not results:
        sys.exit(f"{path} has no results")
    return results


def select_cases(requested):
    if not requested:
        return CASES
    selected = []
    for text in requested:
        shape, _, fields = text.partition(":")
        case = (shape, int(fields)) if fields.isdigit() else None
        if case not in CASES:
            sys.exit(f"unknown case {text!r}; the cases are " + ", ".join(f"{s}:{n}" for s, n in CASES))
        selected.append(case)
    return tuple(selected)


def head(root):
    return subprocess.run(["git", "-C", str(root), "rev-parse", "--short=12", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--forks", type=int, default=10, help="cold JVMs per case (default 10)")
    parser.add_argument("--json", help="keep the JMH JSON in this file (default: a temporary file)")
    parser.add_argument("--from", dest="source", help="report this JMH JSON file instead of running the benchmarks")
    parser.add_argument("--previous", help="JMH JSON of an earlier run: print the change per case")
    parser.add_argument("--baseline", help="JMH JSON of another run: print the change per case")
    parser.add_argument("--reference-root", type=Path, help="checkout of the reference commit: build it too, run both builds in turn and "
                                                          "print the change against it")
    parser.add_argument("--reference-json", help="keep the JMH JSON of the reference build in this file (default: next to --json)")
    parser.add_argument("--case", action="append", metavar="SHAPE:FIELDS",
                        help="run this case only, for example rings:100 (repeatable; default: every case)")
    args = parser.parse_args()
    if args.source and args.reference_root:
        parser.error("--from reports a JSON that exists: pass the reference's JSON as --baseline")

    if args.source:
        json_path = args.source
        baseline = args.baseline
        baseline_title = "the baseline run"
    else:
        json_path = args.json or str(Path(tempfile.mkdtemp(prefix="startup-report-")) / "jmh.json")
        reference_json = args.reference_json or str(Path(json_path).with_name("startup-reference.json"))
        selected = select_cases(args.case)
        run_jmh(json_path, args.forks, selected, args.reference_root, reference_json)
        print(f"JMH JSON: {json_path}")
        baseline = reference_json if args.reference_root else args.baseline
        baseline_title = "the reference build of this run" if args.reference_root else "the baseline run"
    results = load(json_path)
    print_medians(results, sys.stdout)
    if args.reference_root:
        print(f"Reference build: commit {head(args.reference_root)} against this checkout, {head(ROOT)}, rounds in turn on one machine")
    if args.previous:
        print_change(results, load(args.previous), sys.stdout)
    if baseline:
        print_change(results, load(baseline), sys.stdout, baseline_title)


if __name__ == "__main__":
    main()
