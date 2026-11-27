# test-suite-build

Gradle project `:test-suite-build`, folder `test-suite-build/`. Not published.

## Purpose

The proof that the build wiring of the compile-time checks works. Each sample is a small project that applies
`micronaut-graphql-tools-processor` the way a consumer's build does, and a spec builds it with Gradle TestKit or Maven
against the library published to `build/repo`. The tagged regions of the sample build files (`// tag::schema-dirs[]`, ...) are the
snippets that the compile-time guide page (#12) includes, so a documented snippet is one that a spec has built.

## Responsibility

- `samples/<name>`: one standalone Gradle build per tool, each with a Book resolver and a one-file schema in
  `src/main/resources/graphql`:

  | Sample | Processor path | How the option reaches the compiler | Schema as a task input |
  | --- | --- | --- | --- |
  | `java-gradle` | `annotationProcessor` | `-A` through a `CommandLineArgumentProvider` | `@InputFiles`, relative path sensitivity, on the provider |
  | `kotlin-ksp` | `ksp` | `ksp { arg(...) }` | explicit `inputs.files` on the `ksp*Kotlin` tasks |
  | `kotlin-kapt` | `kapt` | `kapt { arguments { arg(...) } }` | explicit `inputs.files` on `kaptKotlin` and `kaptGenerateStubsKotlin` |
  | `groovy-gradle` | `compileOnly` | `-D` in the `jvmArgs` of the forked `compileGroovy` | explicit `inputs.files` on `compileGroovy` |
  | `multi-module-gradle` | `annotationProcessor` of each module with resolvers | as `java-gradle`, reading the schema of another module | as `java-gradle` |

  `multi-module-gradle` has five modules: `schema` (the schema file), `model` (the classes the resolvers return and take: an
  object type, an enum, an input class), `books` and `authors` (resolver classes, one of them a type resolver for a model class
  of another module) and `app` (starts the application with `./gradlew :app:run`). Only `books` and `authors` apply the processor and
  the snippet, each with the schema directory of `schema`; `model` and `app` do not, so a schema edit leaves them up to date.
  What a feature module cannot know, a field that no module resolves, is reported by the startup of `app`. The snippet also
  passes `module.dir` (the project directory of the module), which lets the visitor reject a `schema.dirs` that an earlier module of the
  same JVM left behind.

- `samples/multi-module-maven`: a Maven reactor with `schema` (the schema file) and two modules with resolvers that read it and
  pass `schema.dirs` and `module.dir`. `MultiModuleMavenSampleTest` builds it, and builds it again with the snippet removed from the
  module built last: Maven compiles the reactor in one JVM, so without the check that module would pass against the schema of the one before it.
- `samples/maven-plugin-<language>` (`maven-plugin-java`, `maven-plugin-kotlin`, `maven-plugin-groovy`): Maven projects that declare the
  `generate` goal of `micronaut-graphql-tools-maven-plugin` (the tagged `plugin` snippet of the guide) and compile with their language. The
  generator emits no source file yet, so each sample copies one source file of its language into the generated directory in the
  same phase, before the goal, and `Main` uses it: the build proves that the registered source root is compiled. `MavenPluginSampleContract`
  asserts the goal runs at `generate-sources`, an unchanged run is skipped and a changed schema runs again, `-Dmicronaut.graphql.tools.codegen.package.name`
  and `.skip` bind, a missing option and a syntax error fail with their diagnostics; the Groovy sample shows that GMavenPlus ignores the
  compile source roots (the `sources` snippet is required), and the Kotlin sample records that the Kotlin plugin reads them even with `sourceDirs`.
- `samples/<name>-maven` (`java-maven`, `kotlin-kapt-maven`, `groovy-maven`): Maven projects with a Micronaut 4 parent.
  Java passes the option as a `-A` compiler argument and puts the processor on `annotationProcessorPaths`; Kotlin passes
  `annotationProcessorArgs` to the `kapt` execution of `kotlin-maven-plugin`; Groovy puts the processor on the `provided`
  scope and sets the option as a system property of the Maven JVM with `properties-maven-plugin`. The library comes from
  `build/repo` as a snapshot repository, and the library BOM manages the processor version.
- `src/test/java/.../build`: `GradleSampleContract` is what every Gradle sample must show: the option arrives (the
  visitor's summary note names the schema directory), a schema edit reruns the compile task, a drifted class fails with
  `[code] coordinate` and the schema location, a missing option fails with `processor-schema-not-configured`,
  `checks=false` compiles with one note, and the build cache outcome of a clean rebuild and of a relocated project is
  recorded (it is asserted for javac: `FROM_CACHE`). One subclass per sample names the compile task.
  `MavenSampleContract` is the Maven counterpart: the option arrives, a drifted class fails `mvn clean verify` with
  `[code] coordinate`, a missing option fails, and `-Dgraphql.checks=false` passes with the note. Maven does not track the
  schema directory: the compiler plugin of a Java project calls the classes up to date after a schema edit, so the
  documented rule is `mvn clean verify` after a schema edit. The spec shows it, and records what each sample does without
  `clean`; the startup still runs every check.
  `IncrementalCompilationContract` (one subclass per Gradle sample: Java, kapt, KSP, Groovy) is the incrementality proof (#13): it builds a copy of
  the sample with 22 classes twice, with the processor and as a control without it, applies the same edits to both (the body of a
  bean, the body of the resolver, the binary interface of `Book`, a new class with a stereotype the visitor does not claim, a
  deleted class) and asserts that the set of class files an incremental build rewrote (modification time, size or presence) is
  the same in both. Each state is also built from scratch in a copy, and must pass: an incremental build never fails where a clean
  build passes. A schema edit that drifts the resolver fails the incremental build with the code and the coordinate, and the repair
  passes. The specs record the clean and incremental times, with the JDK and the machine load, in `outcomes.txt`; they assert no time.
  `KotlinKspLooseNullabilityTest` and `JavaMavenLooseNullabilityTest` cover the processor option `loose.nullability`: the `kotlin-ksp`
  sample (`-PgraphqlLooseNullability`) passes by default and with `warn` and fails with `fail` on `Query.book`, a non-null return type at a nullable
  field; the `java-maven` sample (`-Dgraphql.looseNullability`) accepts every value and fails on a bad one. Maven cannot show the effect: kapt reads
  Kotlin classes as Java stubs. The guide page of the options includes their tagged snippets.
  `MultiModuleGradleSampleTest` adds, on top of the contract: every module compiles and `app` starts and answers a query through
  the resolvers of both feature modules; a schema edit reruns `books` and `authors` and leaves `model` and `app` up to date; a
  wrong argument type, a wrong argument name, or a model enum that drifts from the schema fails the compile task of the module
  whose resolver is affected, with the code and the coordinate, while the module next to it still compiles (`--continue`); a field
  with no resolver in any module compiles everywhere and fails `:app:run` with the coordinate; and a module that forgot the
  snippet, built after one that set it in the same daemon, fails with `processor-schema-not-configured` and its own directory.
- `profiler/`: the compile-time profile and budget (#418). `compile-time.scenarios` holds the gradle-profiler scenarios (a clean build, a
  body edit and a schema edit, each with the checks on and off, for `java-gradle`, `kotlin-ksp`, `kotlin-kapt`, `groovy-gradle` and
  `multi-module-gradle`); `profiler.init.gradle.kts` makes the edits inside the measured build and records the time of each compile task;
  `compile-time-budget.properties` is the budget; `CompileTimeBudget` compares the medians with it and writes the per-module report.
  `./gradlew :test-suite-build:compileTimeBudget -PgradleProfiler=<path to gradle-profiler>` runs all of it (CI job `compile-time budget`).
  `CompileTimeBudgetTest` runs in `buildToolsTest`: the scenario definitions and the budget check, with a measurement that exceeds its budget.
- `profiler/large-schema/`: the large-schema spike (#665), a standalone Gradle build that is not part of `buildToolsTest`. `probe` holds one
  workload (a generated schema of 2,000 types and 250 operation documents; graphql-java parses and builds the schema, then parses and validates
  the documents) and three hosts for it: a javac processor, a KSP processor provider and a Groovy global AST transformation. `javac`, `ksp` and
  `groovy` are tiny projects with the probe on the processor path of their compiler. `run.sh [runs]` builds each in a Gradle JVM of its own
  and writes `timings.txt` (medians, the worst schema build, every run, the JDK and the machine load). It needs the network once for the
  Kotlin and KSP plugins: `mise exec -- test-suite-build/profiler/large-schema/run.sh 5`. It asserts nothing; the budgets it is read against are
  in #665 (cold schema build at most 2 s, warm validation of 250 documents at most 100 ms). The committed `timings.txt` is one run on a busy
  machine (load average about 30 to 38), so read it as a sample of that run, not as a general result; rerun `run.sh` on an idle machine to compare.
- The samples resolve the library from `build/repo`, as published, so the processor jar they use is the real one: it registers the visitor and only the visitor.

Not in this module: the visitor and its options (`graphql-tools-processor`), the binding corpus, and the example applications.

## Who uses it

Maintainers and CI (the gated job `build tools`). Application developers copy the tagged regions of the sample build files.

## Public and internal

Nothing here is a library API.

## Relation to other modules

- Publishes nothing, depends on no other module at build time of its own sources. `buildToolsTest` depends on the
  `publishAllPublicationsToBuildRepository` tasks of `graphql-tools`, `graphql-tools-binding`, `graphql-tools-processor`
  and `graphql-tools-bom`.
- The plugin versions of the samples are read from the root `build.gradle.kts`, the Micronaut platform version from
  `gradle/libs.versions.toml`.

## Build and test

JDK 25 (the CI job's JDK) or 21 and `mvn` on the path (`mise install` installs both); the specs need the network (the plugin portal and Maven Central) and take a few minutes.

```
./gradlew :test-suite-build:buildToolsTest
```

`build` and `check` do not run the specs (a sample build per spec is slow, and the graphql-java legs do not change what
they prove); `check` runs the license header check. The task is up to date never: it publishes the library first.
`build/reports/build-tools/outcomes.txt` lists the recorded outcomes.
