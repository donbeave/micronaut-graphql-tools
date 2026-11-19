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
  `MultiModuleGradleSampleTest` adds, on top of the contract: every module compiles and `app` starts and answers a query through
  the resolvers of both feature modules; a schema edit reruns `books` and `authors` and leaves `model` and `app` up to date; a
  wrong argument type, a wrong argument name, or a model enum that drifts from the schema fails the compile task of the module
  whose resolver is affected, with the code and the coordinate, while the module next to it still compiles (`--continue`); a field
  with no resolver in any module compiles everywhere and fails `:app:run` with the coordinate; and a module that forgot the
  snippet, built after one that set it in the same daemon, fails with `processor-schema-not-configured` and its own directory.- The samples resolve the library from `build/repo`, as published, so the processor jar they use is the real one: it registers the visitor and only the visitor.

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
