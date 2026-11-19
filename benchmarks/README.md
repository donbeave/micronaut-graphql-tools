# benchmarks

Gradle project `:benchmarks`, folder `benchmarks/`. Not published.

## Purpose

Measurements of the request hot path and of start-up, plus the allocation gate that protects the hot path.
Performance is a critical property of the library. Maintainers use this module when they change code that a request
runs, or when they claim a performance gain.

## Responsibility

- `src/jmh/java` (package `io.micronaut.graphql.tools.benchmarks`): the JMH benchmarks. They cover property, root and
  type resolver methods, argument conversion, union and interface resolution, a directive-wrapped fetcher, whole queries,
  and start-up (`BindingBenchmark`, `RetainedHeapBenchmark`).
- `src/allocationTest/java`: the allocation gate. It fails when an operation allocates more bytes than its baseline in
  `allocation-baselines.properties`.
- `src/jmhSmokeTest/java`: runs every JMH benchmark once, so that the benchmarks compile and start. Its numbers mean nothing.
- `src/main`: the benchmark classes shared by the source sets (`Resolvers`, `BenchmarkCases`, `Fixtures`). The
  `generateLargeSchema` task writes the large schema that the start-up benchmarks bind to `build/generated/large-schema`.
- `benchmarks/scripts/startup-report.py`: builds one jar per schema size and runs the start-up benchmarks cold, one JVM per sample.

## Packages

Benchmark code, not a library package: no `package-info.java`. Nothing here is API.

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.benchmarks` | Test helper | The JMH benchmarks, the allocation tests and the shared benchmark classes |

## Who uses it

Maintainers and CI. The CI `check` runs `jmhSmokeTest` and `allocationTest`. No application depends on this module.

## Public and internal

Nothing here is a library API. The module is not in the BOM (`graphql-tools-bom`).

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`), the library under measurement, and on `micronaut-inject`
  (annotation processing for the benchmark beans).
- JMH is the benchmark framework (`jmh` and `jmhAnnotationProcessor` configurations).
- Nothing depends on this module.

## Build and test

Run from the repository root. JDK 21 or 25.

```
./gradlew :benchmarks:jmh
./gradlew :benchmarks:jmh -Pjmh.profile=quick
./gradlew :benchmarks:jmhSmokeTest
./gradlew :benchmarks:allocationTest
```

- `:benchmarks:jmh` runs two forks with the `gc` profiler. The allocation figure is `gc.alloc.rate.norm`. It excludes `BindingBenchmark` and
  `RetainedHeapBenchmark`. Its warm-up makes `ContextStartBenchmark`, `FirstRequestBenchmark` and
  `StartAndFirstRequestBenchmark` invalid as cold measurements: run the cold start-up benchmarks with
  `benchmarks/scripts/startup-report.py` or the `jmhJar` jar as the "Benchmarks" section of `CONTRIBUTING.md` describes.
- `:benchmarks:allocationTest -Pallocation.update` rewrites the baselines. Review the diff: a higher number is an
  allocation the change added. Use `-PgraphqlJavaLeg=floor` or `ceiling` for the other legs, which have their own
  baselines.
- `:benchmarks:jmhJar` builds the benchmark jar.
- `-PlargeFields=<fields>` generates the start-up schema of another size, one size per build; `-PlargeShape=interfaces` generates the interface-heavy schema instead of the rings.

## Measured start-up results

Cold start of a context that binds a generated schema, `BindingBenchmark` (`record=false`, one fresh JVM per fork, `-wi 0 -i 1
-bm ss`) and `RetainedHeapBenchmark`, measured with `scripts/startup-ab.py`: 25 forks per build, the builds interleaved in
a rotating order. Oracle GraalVM 25.0.5 (JDK 25), graphql-java default leg, Apple M1 Max (10 cores, 64 GB), shared with
other users' processes: the one-minute load average at the start of a fork had a median of 8 (6 to 32). Allocation is `gc.alloc.rate.norm` of
`-prof gc`, 5 forks per build (the figures of one build vary by less than 3 percent between forks). Each pair differs by one commit; the benchmark sources of the current master were
applied to each checkout with the package names of that checkout. "Before" and "after" are medians; the interval is a 95 percent
bootstrap interval of the median difference.

Binding of the interface-heavy and ring schemas, before and after the removal of the quadratic work (the commit before 26657623 and 26657623):

| Schema | Before | After | Difference | Interval | Allocation before, after | Retained heap before, after |
| --- | --- | --- | --- | --- | --- | --- |
| interfaces, 3,000 fields | 581.9 ms | 562.1 ms | -19.8 ms (-3.4%) | -28.7 to -15.2 | 171.7 MB, 158.8 MB (-7.5%) | 6.20 MB, 6.14 MB |
| interfaces, 20,000 fields | 1662.9 ms | 1466.5 ms | -196.4 ms (-11.8%) | -221.8 to -184.3 | 942.2 MB, 829.4 MB (-12.0%) | 21.37 MB, 21.31 MB |
| rings, 20,000 fields | 1698.4 ms | 1700.6 ms | +2.2 ms (+0.1%) | -14.9 to +15.9 | 795.8 MB, 787.0 MB (-1.1%) | 31.76 MB, 32.68 MB |

The rings show no change in time (inside the noise): they barely reach the changed paths. Their retained heap is 0.9 MB (2.9%) higher after the change in every fork (31.56 to 32.17 MB before, 32.59 to 33.31 MB after), although the allocation is lower; it is reported, not explained. The time grows with an exponent of
0.55 (before) and 0.51 (after) over the interface schema from 3,000 to 20,000 fields (`ln(t20000 / t3000) / ln(20000 / 3000)` of the
medians); a part of both times is the fixed cost of starting a JVM and a context, so the exponent understates the growth of the binding itself.

Customizer replacement snapshot, before and after skipping it with no user customizer (the commit before 675c52c and 675c52c):

| Schema | Before | After | Difference | Interval | Allocation before, after |
| --- | --- | --- | --- | --- | --- |
| interfaces, 3,000 fields | 688.7 ms | 674.1 ms | -14.6 ms (-2.1%) | -20.7 to -2.3 | 185.8 MB, 184.2 MB |
| interfaces, 20,000 fields | 1735.3 ms | 1696.3 ms | -39.0 ms (-2.2%) | -55.7 to -17.7 | 956.9 MB, 946.3 MB (-1.1%) |
| rings, 20,000 fields | 1804.8 ms | 1766.5 ms | -38.3 ms (-2.1%) | -66.8 to -12.3 | 824.3 MB, 813.6 MB (-1.3%) |

At 20,000 fields the saving is about 38 ms, which is the roughly 42 ms the snapshot was measured to cost (the interval,
12 to 67 ms, holds 42 ms). The retained heap does not change (21.87 MB and 21.86 MB for the interface schema).

The full description, including the cold start procedure, is the "Benchmarks" section of `CONTRIBUTING.md`.
