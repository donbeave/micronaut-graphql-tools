#!/usr/bin/env python3
"""Cold start of the declarative GraphQL client against plain @Client (#686): interleaved cold forks, medians and a bootstrap interval.

`ClientColdStartBenchmark` starts a context that holds 10 clients of 20 operations each and resolves every client, one operation
per fork (single shot, a fresh JVM). Build one jar per mode (the mode is a build property, so a JVM sees one mode's beans only),
copy it away, because the next build replaces it, then run:

  ./gradlew :test-suite-benchmarks:jmhJar -PcoldStartMode=plain
  cp test-suite-benchmarks/build/libs/*-jmh.jar plain.jar
  ./gradlew :test-suite-benchmarks:jmhJar -PcoldStartMode=declarative
  cp test-suite-benchmarks/build/libs/*-jmh.jar declarative.jar
  python3 test-suite-benchmarks/scripts/client-coldstart-ab.py --forks 40 plain=plain.jar declarative=declarative.jar

Each round starts one cold JVM per jar, in a rotating order, so every mode takes every position and a slow stretch of the machine
hits both alike. The report gives the median per mode with quartiles and range, and for each later mode against the first the
median difference with a 95 percent bootstrap interval. The budget is a median difference of at most +5 ms; an interval that holds
zero is inside the noise. The load average at the start of every fork is printed: quote it with the figures. The JDK is the `java`
on the path. The run never gates a build: a start-up time depends on the machine.
"""

import argparse
import json
import os
import random
import statistics
import subprocess
import tempfile
from pathlib import Path

BENCHMARK = "ClientColdStartBenchmark"
BUDGET_MS = 5.0


def fork(jar, out):
    subprocess.run(["java", "-jar", jar, BENCHMARK, "-f", "1", "-wi", "0", "-i", "1", "-bm", "ss", "-rf", "json", "-rff", str(out)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return json.loads(out.read_text(encoding="utf-8"))[0]["primaryMetric"]["rawData"][0][0]


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
    parser.add_argument("builds", nargs="+", metavar="name=jar", help="two or more modes; the first is the base")
    parser.add_argument("--forks", type=int, default=30, help="cold JVMs per mode (default 30)")
    args = parser.parse_args()
    names = [b.split("=", 1)[0] for b in args.builds]
    jars = {b.split("=", 1)[0]: b.split("=", 1)[1] for b in args.builds}
    ms = {n: [] for n in names}
    loads = []
    with tempfile.TemporaryDirectory(prefix="client-coldstart-") as tmp:
        for r in range(args.forks):
            shift = r % len(names)
            for n in names[shift:] + names[:shift]:
                loads.append(os.getloadavg()[0])
                ms[n].append(fork(jars[n], Path(tmp) / "out.json"))
    print(f"{args.forks} forks per mode, load average (1 minute) at fork start "
          f"{min(loads):.1f} to {max(loads):.1f}, median {statistics.median(loads):.1f}")
    for n in names:
        v = ms[n]
        print(f"  {n:<12} median {statistics.median(v):8.1f} ms  quartiles {quartile(v, .25):8.1f} to {quartile(v, .75):8.1f}  "
              f"range {min(v):8.1f} to {max(v):8.1f}")
    for n in names[1:]:
        lo, hi = bootstrap(ms[names[0]], ms[n])
        diff = statistics.median(ms[n]) - statistics.median(ms[names[0]])
        print(f"  {names[0]} -> {n}: {diff:+.1f} ms, 95% interval {lo:+.1f} to {hi:+.1f} "
              f"({'inside the noise' if lo <= 0 <= hi else 'outside the noise'}); budget +{BUDGET_MS:.0f} ms: "
              f"{'met' if diff <= BUDGET_MS else 'NOT met'}")


if __name__ == "__main__":
    main()
