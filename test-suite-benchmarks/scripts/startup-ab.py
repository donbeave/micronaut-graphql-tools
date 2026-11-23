#!/usr/bin/env python3
"""A/B start-up comparison of two builds on a noisy machine (#368): interleaved cold forks, medians and a bootstrap interval.

Build one benchmark jar per checkout and case (`./gradlew :test-suite-benchmarks:jmhJar -PlargeFields=<fields> -PlargeShape=<shape>`,
then copy `test-suite-benchmarks/build/libs/*-jmh.jar` away: the next build replaces it), then run

  python3 test-suite-benchmarks/scripts/startup-ab.py --fields 20000 --shape interfaces --forks 25 base=base.jar branch=branch.jar

Each round starts one cold JVM per jar, in a rotating order, so every build takes every position and a slow stretch of the
machine hits all builds alike. The report gives, per build, the median binding time with the quartiles and the range, the median
retained heap, and for each later build against the first the median difference with a 95 percent bootstrap interval. A
difference whose interval holds zero is inside the noise: do not report it as a gain. The load average at the start of every
fork is read from the operating system, and its range is printed: quote it with the figures. The JDK is the `java` on the path.
"""

import argparse
import json
import os
import random
import statistics
import subprocess
import tempfile
from pathlib import Path

BENCHMARKS = "BindingBenchmark|RetainedHeapBenchmark"


def fork(jar, shape, fields, out):
    subprocess.run(["java", "-jar", jar, BENCHMARKS, "-p", f"fields={fields}", "-p", f"shape={shape}", "-p", "record=false",
                    "-f", "1", "-wi", "0", "-i", "1", "-bm", "ss", "-rf", "json", "-rff", str(out)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ms = heap = None
    for result in json.loads(out.read_text(encoding="utf-8")):
        if result["benchmark"].endswith("bindLargeSchema"):
            ms = result["primaryMetric"]["rawData"][0][0]
        else:
            heap = result["secondaryMetrics"]["retainedBytes"]["rawData"][0][0]
    return ms, heap


def quartile(values, p):
    ordered = sorted(values)
    index = (len(ordered) - 1) * p
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (index - low)


def bootstrap(base, other, draws=5000):
    rng = random.Random(1)
    diffs = sorted(statistics.median(rng.choices(other, k=len(other))) - statistics.median(rng.choices(base, k=len(base)))
                   for _ in range(draws))
    return diffs[int(draws * 0.025)], diffs[int(draws * 0.975)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("builds", nargs="+", metavar="name=jar", help="two or more builds; the first is the base")
    parser.add_argument("--fields", type=int, required=True)
    parser.add_argument("--shape", choices=("rings", "interfaces"), required=True)
    parser.add_argument("--forks", type=int, default=25, help="cold JVMs per build (default 25)")
    args = parser.parse_args()
    names = [b.split("=", 1)[0] for b in args.builds]
    jars = {b.split("=", 1)[0]: b.split("=", 1)[1] for b in args.builds}
    ms = {n: [] for n in names}
    heap = {n: [] for n in names}
    loads = []
    with tempfile.TemporaryDirectory(prefix="startup-ab-") as tmp:
        for r in range(args.forks):
            shift = r % len(names)
            for n in names[shift:] + names[:shift]:
                loads.append(os.getloadavg()[0])
                t, h = fork(jars[n], args.shape, args.fields, Path(tmp) / "out.json")
                ms[n].append(t)
                heap[n].append(h)
    print(f"{args.shape} fields={args.fields:,}, {args.forks} forks per build, load average (1 minute) at fork start "
          f"{min(loads):.1f} to {max(loads):.1f}, median {statistics.median(loads):.1f}")
    for n in names:
        v = ms[n]
        print(f"  {n:<12} median {statistics.median(v):8.1f} ms  quartiles {quartile(v, .25):8.1f} to {quartile(v, .75):8.1f}  "
              f"range {min(v):8.1f} to {max(v):8.1f}  retained {statistics.median(heap[n]) / 1e6:6.2f} MB")
    for n in names[1:]:
        lo, hi = bootstrap(ms[names[0]], ms[n])
        diff = statistics.median(ms[n]) - statistics.median(ms[names[0]])
        print(f"  {names[0]} -> {n}: {diff:+.1f} ms, 95% interval {lo:+.1f} to {hi:+.1f} "
              f"({'inside the noise' if lo <= 0 <= hi else 'outside the noise'})")


if __name__ == "__main__":
    main()
