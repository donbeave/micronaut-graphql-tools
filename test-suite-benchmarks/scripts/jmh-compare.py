#!/usr/bin/env python3
"""Compares two JMH JSON result files and prints a Markdown table.

    jmh-compare.py CURRENT.json [--previous PREVIOUS.json] [--threshold PERCENT] [--note TEXT ...]

For each benchmark (with its parameters) the table shows the score of both runs, the change in percent, the 99.9 percent
error that JMH reports for each score (the spread of the repeated runs), and the bytes allocated per operation
(`gc.alloc.rate.norm`, from the gc profiler) of both runs. A row is flagged as a regression when the score is worse by
more than the threshold (default 10) and the two scores differ by more than the sum of their errors, so a change inside
the noise is not flagged. Lower is better for the average-time, sample-time and single-shot modes; higher is better for
throughput. A benchmark whose mode or score unit differs between the two files is not compared: its row says so.
The exit status is 0 either way: the report informs, it does not gate.
"""
import argparse
import json
import sys


def load(path):
    with open(path) as f:
        return json.load(f)


def key(result):
    params = ",".join(f"{k}={v}" for k, v in sorted((result.get("params") or {}).items()))
    return result["benchmark"].rsplit(".", 1)[-1] + (f" [{params}]" if params else "")


def alloc(result):
    metric = (result.get("secondaryMetrics") or {}).get("·gc.alloc.rate.norm") or (result.get("secondaryMetrics") or {}).get("gc.alloc.rate.norm")
    return metric["score"] if metric else None


def error(result):
    e = result["primaryMetric"].get("scoreError")
    return 0.0 if e is None or e != e else e


def fmt(value, digits=3):
    return "-" if value is None else f"{value:.{digits}f}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("current")
    parser.add_argument("--previous")
    parser.add_argument("--threshold", type=float, default=10.0)
    parser.add_argument("--note", action="append", default=[])
    args = parser.parse_args()

    current = load(args.current)
    previous = load(args.previous) if args.previous else []
    previous_by_key = {key(r): r for r in previous}

    first = current[0] if current else {}
    print(f"JDK {first.get('jdkVersion', '?')}, {first.get('vmName', '?')} {first.get('vmVersion', '')}".rstrip())
    print(f"forks {first.get('forks', '?')}, warm-up iterations {first.get('warmupIterations', '?')}, "
          f"measurement iterations {first.get('measurementIterations', '?')}, threshold {args.threshold:g} percent")
    for note in args.note:
        print(note)
    print()
    print("| Benchmark | Previous | Current | Change | Error (previous / current) | B/op (previous / current) | |")
    print("| --- | ---: | ---: | ---: | ---: | ---: | --- |")
    regressions = []
    for result in sorted(current, key=key):
        name = key(result)
        metric = result["primaryMetric"]
        score, unit = metric["score"], metric["scoreUnit"]
        old = previous_by_key.get(name)
        flag, change, old_score, old_error, old_alloc = "", "-", None, None, None
        if old is not None and (old["mode"] != result["mode"] or old["primaryMetric"]["scoreUnit"] != unit):
            change = f"mode or unit changed ({old['mode']} {old['primaryMetric']['scoreUnit']} to {result['mode']} {unit})"
            old = None
        if old is not None:
            old_score, old_error, old_alloc = old["primaryMetric"]["score"], error(old), alloc(old)
            if old_score:
                delta = (score - old_score) / old_score * 100.0
                change = f"{delta:+.1f}%"
                worse = delta > args.threshold if result["mode"] != "thrpt" else -delta > args.threshold
                if worse and abs(score - old_score) > error(result) + old_error:
                    flag = "regression"
                    regressions.append((name, old_score, score, unit, change))
        print(f"| {name} | {fmt(old_score)} | {fmt(score)} {unit} | {change} | "
              f"{fmt(old_error)} / {fmt(error(result))} | {fmt(old_alloc, 1)} / {fmt(alloc(result), 1)} | {flag} |")
    print()
    if not previous:
        print("No previous run to compare with.")
    elif regressions:
        print(f"{len(regressions)} regression(s) above {args.threshold:g} percent:")
        for name, old_score, score, unit, change in regressions:
            print(f"- {name}: {old_score:.3f} -> {score:.3f} {unit} ({change})")
    else:
        print(f"No regression above {args.threshold:g} percent outside the reported error.")
    only_old = sorted(set(previous_by_key) - {key(r) for r in current})
    if only_old:
        print(f"Benchmarks missing from the current run: {', '.join(only_old)}")


if __name__ == "__main__":
    sys.exit(main())
