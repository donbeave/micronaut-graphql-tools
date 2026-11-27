# benchmarks

Gradle project `:test-suite-benchmarks`, folder `benchmarks/`. Not published.

## Purpose

Measurements of the request hot path and of start-up, plus the allocation gate that protects the hot path.
Performance is a critical property of the library. Maintainers use this module when they change code that a request
runs, or when they claim a performance gain.

## Responsibility

- `src/jmh/java` (package `io.micronaut.graphql.tools.benchmarks`): the JMH benchmarks. They cover property, root and
  type resolver methods, argument conversion, union and interface resolution, a directive-wrapped fetcher, whole queries,
  start-up (`BindingBenchmark`, `RetainedHeapBenchmark`), and the executor hop (`InvocationBenchmark`,
  `AsyncBoundaryBenchmark`; plain graphql-java, no library code, no allocation gate), the parsed-document cache of
  `protection.document-cache-size` (`DocumentCacheBenchmark`: the same query text with the cache off and on), and the metrics and tracing modules on a
  request with no field traced (`ObservabilityBenchmark`, modes `none`, `empty` and `observed`; its allocation gate is
  `observedInvocationSlope`: the bytes per field that the modules add, which must stay 0) and with every root field selected for a
  span by the field decorator (mode `selected`: its difference to `observed`, divided by `rootFields`, is the cost of one field span),
  the field decorators on one query of 1,050 fields (`FieldDecoratorBenchmark`: no decorator bean, one that selects no field, one that
  wraps every field; each configuration checks its result against the first and what the decorator did at startup; no allocation gate),
  and the per-class binding rules over a compiler `ClassElement` (`BindingRulesBenchmark`: a resolver class, an input class with
  properties and an input class with constructor arguments; its allocation gate is `BindingRulesAllocationTest`),
  and the codec of the GraphQL client (`ClientCodecBenchmark`: encode a request, decode data, decode with the single root field unwrapped, decode errors; its allocation gates are `clientEncodeRequest`, `clientDecodeData`, `clientDecodeUnwrap` and `clientDecodeErrors`; unwrapping may add at most 80 B/op to the data decode),
  and a blocking call of an embedded server that answers a fixed body of 1 KB (`ClientLoopbackBenchmark`: the declarative client, the low-level `GraphQLHttpClient` and a plain declarative `@Client` as the reference; the classes are in the package `loopback`, active only with `benchmarks.loopback=true`; its allocation gates are `clientDeclarativeLoopback`, `clientLowLevelLoopback` and `clientRawLoopback`, the first two measured in one JVM with the third: at most +512 B/op and +256 B/op on the calling thread; the threads of the event loop are not counted by the gate, so read `-prof gc` for the total; the figures differ between macOS and Linux, so the baselines of these three cases are recorded for macOS only (`.macos` in the key) and the other platforms enforce the budgets alone).
- `src/allocationTest/java`: the allocation gate. It fails when an operation allocates more bytes than its baseline in
  `allocation-baselines.properties`.
- `src/jmhSmokeTest/java`: runs every JMH benchmark once, so that the benchmarks compile and start. Its numbers mean nothing.
- `src/main`: the benchmark classes shared by the source sets (`Resolvers`, `BenchmarkCases`, `Fixtures`), and `ClassElementFixture`: compiler
  class elements of fixture sources, compiled in process, for the binding rules. The compilation runs on a thread that stays parked inside its
  processing round until the fixture is closed, because a class element is valid only during its round, and the class files are written
  when the round is released. The fixture is in a package of this module (`benchmarks.fixture`) and reaches the element adapter
  (`ElementTypeModel`, package-private) through `BindingRulesAccess`, a public class of the processor that is documented as internal. `BindingRulesParityTest` (in `jmhSmokeTest`) shows that
  the rules give the same findings for the elements as for the classes that the sources compile to. The
  `generateLargeSchema` task writes the large schema that the start-up benchmarks bind to `build/generated/large-schema`.
- `test-suite-benchmarks/scripts/startup-report.py`: builds one jar per schema size and runs the start-up benchmarks cold, one JVM per sample.
- `test-suite-benchmarks/scripts/jmh-compare.py`: compares two JMH JSON files (the nightly workflow's report) and flags a score that is worse by more than a threshold outside the reported error.

## Packages

Benchmark code, not a library package: no `package-info.java`. Nothing here is API.

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.benchmarks` | Test helper | The JMH benchmarks, the allocation tests and the shared benchmark classes |
| `io.micronaut.graphql.tools.benchmarks.fixture` | Test helper | The in-process compilation that yields compiler class elements for the binding rules benchmark |

## Who uses it

Maintainers and CI. The CI `check` runs `jmhSmokeTest` and `allocationTest`. No application depends on this module.

## Public and internal

Nothing here is a library API. The module is not in the BOM (`graphql-tools-bom`).

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`), the library under measurement, on `micronaut-graphql-tools-client` and
  Micronaut Serialization (the client codec cases), on the Netty server and the HTTP client (the loopback cases), and on `micronaut-inject` (annotation processing for the benchmark beans).
- JMH is the benchmark framework (`jmh` and `jmhAnnotationProcessor` configurations).
- Nothing depends on this module.

## Build and test

Run from the repository root. JDK 21 or 25.

```
./gradlew :test-suite-benchmarks:jmh
./gradlew :test-suite-benchmarks:jmh -Pjmh.profile=quick
./gradlew :test-suite-benchmarks:jmhSmokeTest
./gradlew :test-suite-benchmarks:allocationTest
```

- `:test-suite-benchmarks:jmh` runs two forks with the `gc` profiler. The allocation figure is `gc.alloc.rate.norm`. It excludes `BindingBenchmark` and
  `RetainedHeapBenchmark`. Its warm-up makes `ContextStartBenchmark`, `FirstRequestBenchmark` and
  `StartAndFirstRequestBenchmark` invalid as cold measurements: run the cold start-up benchmarks with
  `test-suite-benchmarks/scripts/startup-report.py` or the `jmhJar` jar as the "Benchmarks" section of `CONTRIBUTING.md` describes.
- `:test-suite-benchmarks:allocationTest -Pallocation.update` rewrites the baselines. Review the diff: a higher number is an
  allocation the change added. Use `-PgraphqlJavaLeg=floor` or `ceiling` for the other legs, which have their own
  baselines.
- `:test-suite-benchmarks:jmhJar` builds the benchmark jar.
- `-PlargeFields=<fields>` generates the start-up schema of another size, one size per build; `-PlargeShape=interfaces` generates the interface-heavy schema instead of the rings; add `-PlargeUnreturnedShared=true` to leave the root field `shared` out of that schema, so that the interface `Shared`, which every object type implements, is one that no field returns.

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

## Measured request-path results of the field decorators

`FieldDecoratorBenchmark` (one query of 1,050 fields) and `ObservabilityBenchmark` (a request of 10 and of 1,000 root fields),
rerun for #532 on an idle machine. Command: `./gradlew :test-suite-benchmarks:jmhJar`, then
`java -jar test-suite-benchmarks/build/libs/test-suite-benchmarks-1.0.0-SNAPSHOT-jmh.jar 'FieldDecoratorBenchmark|ObservabilityBenchmark' -f 5 -wi 5 -i 10 -w 1s -r 1s -prof gc`.
Temurin 25.0.5+8, graphql-java default leg, 10-core ARM64 laptop, 5 forks, 50 samples per case. The machine was idle: the one-minute load average
was 2.4 at the start and 2.7 at the end of the 15-minute run (about 91 percent CPU idle before the start), and no other Gradle, native or benchmark job ran.
The score is the mean with the 99.9 percent error that JMH prints.

| FieldDecoratorBenchmark, decorator | us/op | gc.alloc.rate.norm (B/op) |
| --- | --- | --- |
| none (no decorator bean) | 217.358 +- 0.575 | 1289888.7 +- 62.0 |
| unselective (selects no field) | 217.684 +- 0.339 | 1289955.5 +- 53.5 |
| wrapAll (wraps every field) | 220.815 +- 0.944 | 1289941.6 +- 52.3 |

| ObservabilityBenchmark, mode | 10 root fields, us/op | 1,000 root fields, us/op |
| --- | --- | --- |
| none | 2.028 +- 0.009 | 155.136 +- 0.720 |
| empty | 2.034 +- 0.016 | 154.736 +- 0.796 |
| observed | 2.455 +- 0.021 | 143.851 +- 4.567 |
| selected | 4.740 +- 0.030 | 362.877 +- 4.135 |

The intervals now separate the cases. A decorator that selects no field costs nothing measurable (217.7 against 217.4 us, the
intervals overlap, the allocation is equal). Wrapping all 1,050 fields with a delegating wrapper costs about 3.5 us per query (+1.6 percent, intervals do not
overlap), about 3 ns per field, and allocates the same. The tracing and metrics modules with no field traced (`empty`) are within the noise of
no instrumentation at both sizes; `observed` adds about 0.42 us to a 10-field request. At 1,000 fields `observed` measures 11 us
(7 percent) below `none`, with a wider interval; instrumentation cannot make a request faster, so read that as a JIT or
fork-layout effect of this case, not a gain. `selected` adds about 2.3 us for 10 spans and about 219 us for 1,000, so one field span costs
about 0.22 us and about 1.0 KB of allocation (`selected` minus `observed`, divided by `rootFields`).

## Measured start-up cost of an interface that no field returns

#71: the cold-start binding cost of the interface-heavy schema at 20,000 fields with `Shared` returned by a root field (`returned`) and with that
field left out (`unreturned`, `-PlargeUnreturnedShared=true`), so that the `id` and `name` of each of the 2,000 object types (the 4,000 fields `Shared` declares) look up the class of an interface that no field
returns. `Shared` has no introspection, so the dictionary entry `registerType("Shared", Shared.class)` is the only way the lookup finds its class; both builds have the
entry, and `LargeSchema.checkSharedMapped` fails every start-up benchmark that binds the interface shape when the dictionary does not map `Shared` (`BindingBenchmark` runs it after the measurement, in `@TearDown`, so the timed invocation does not include it). (An earlier figure of
this section, +5.6 ms, came from jars without the entry: the lookup found no class and skipped the resolver-method search, so it timed nothing.) The strict check
`unused-dictionary-entry` reports that entry when no resolver binds a field of `Shared`, so the `unreturned` jar accepts that one finding through
`micronaut.graphql.tools.allow.findings`. Two jars, `python3 test-suite-benchmarks/scripts/startup-ab.py --fields 20000 --shape interfaces --forks 25 returned=returned.jar unreturned=unreturned.jar`,
Temurin 25.0.5+8, graphql-java default leg, 10-core ARM64 laptop, no other Gradle job running. Two runs, the second started on a quiet machine: the one-minute
load average at fork start ranged from 6.2 to 14.5 (median 8.4) in the first and from 1.9 to 7.8 (median 4.9) in the second.

| Run | Build | Median binding time | Quartiles | Range | Retained heap |
| --- | --- | --- | --- | --- | --- |
| 1 | returned | 1513.9 ms | 1489.2 to 1520.6 | 1466.6 to 1540.4 | 23.34 MB |
| 1 | unreturned | 1524.8 ms | 1515.2 to 1545.5 | 1486.4 to 1563.1 | 23.33 MB |
| 2 | returned | 1517.7 ms | 1497.5 to 1528.6 | 1479.7 to 1555.6 | 23.35 MB |
| 2 | unreturned | 1529.2 ms | 1520.6 to 1538.7 | 1474.1 to 1546.9 | 23.38 MB |

The difference is +10.9 ms in run 1 (95 percent bootstrap interval +4.2 to +42.4) and +11.5 ms in run 2 (+2.4 to +31.4): about 0.7 percent, outside the noise in both
runs, and retained heap is unchanged. That is the cost of the lookup when it is real: each of the 4,000 fields that `Shared` declares (`id` and `name` of 2,000 object types; the other 16,000 fields are declared by no unreturned interface) asks for the resolver methods of `Shared`, about 2.8 us a lookup (+11 ms over 4,000 lookups).
No resolver bean is bound to `Shared` in this schema, so the run measures the lookup that the unreturned-interface path adds, not the binding of a resolver method.
A schema whose interfaces are all returned by a field never takes this path (the `returned` build).

## The start-up reference

The `startup` job of the `Performance nightly` workflow compares the start-up of the checkout with the commit named in `startup-reference-commit`
(`e0e820c52fe1a0e4d40f2d109dd7926b09d52ae3`), built and run in the same job. A binding time recorded on one machine is not
a reference for another: a time stored from one hosted runner was 50 to 70 percent below the time of the first nightly run on another runner, in every case, while the retained heap
stayed within 1 percent, and the same two commits measured side by side on one machine (Oracle GraalVM 25.0.5.1, Apple M1 Max, 6 cold JVMs per build in turn, rings with 100 and 1,600 fields)
differed by at most 0.8 percent. So `scripts/startup-report.py --reference-root` builds the benchmark jar of both checkouts for every case and starts one cold JVM
of each per round, in a rotating order. Both builds run on the same machine seconds apart, and the report prints the change between the two medians of the run.

- The reference jar is built from the reference commit's own sources, with the same command and the same JDK as the current one. The two commits' start-up benchmarks
  differ by the accessor names of `BindingCode` only.
- The workflow archives both JSON files (`startup-benchmark.json`, `startup-reference.json`) as the `startup-nightly-<ref>` artifact. The change against the last successful run
  is still printed, but it compares two machines: read the change against the reference first.
- To move the reference, write another full commit hash into `startup-reference-commit`. The commit must contain `BindingBenchmark`, `RetainedHeapBenchmark` and
  the `-PlargeFields` and `-PlargeShape` properties of the benchmark build.
- By hand, `python3 test-suite-benchmarks/scripts/startup-report.py --forks 10 --reference-root <checkout of the reference commit> --reference-json reference.json`
  does the same; `--case rings:1600` limits it to one case. Run on a machine that is not idle, and quote the load.

The full description, including the cold start procedure, is the "Benchmarks" section of `CONTRIBUTING.md`.
