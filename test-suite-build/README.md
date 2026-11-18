# test-suite-build

Gradle project `:test-suite-build`, folder `test-suite-build/`. Not published.

## Purpose

The proof that the build wiring of the compile-time checks works. Each sample is a small project that applies
`micronaut-graphql-tools-processor` the way a consumer's build does, and a spec builds it with Gradle TestKit against the
library published to `build/repo`. The tagged regions of the sample build files (`// tag::schema-dirs[]`, ...) are the
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
  | `multi-module-gradle` | `annotationProcessor` of the module with the resolvers | as `java-gradle`, reading the schema of another module | as `java-gradle` |

- `src/test/java/.../build`: `GradleSampleContract` is what every Gradle sample must show: the option arrives (the
  visitor's summary note names the schema directory), a schema edit reruns the compile task, a drifted class fails with
  `[code] coordinate` and the schema location, a missing option fails with `processor-schema-not-configured`,
  `checks=false` compiles with one note, and the build cache outcome of a clean rebuild and of a relocated project is
  recorded (it is asserted for javac: `FROM_CACHE`). One subclass per sample names the compile task.
- `stageSampleRepository`: copies `build/repo` for the samples and registers the visitor in the processor jar's copy. Until the
  registration switch (#291) the published jar still registers the JSR-269 processor, so the copy drops that registration
  and adds the visitor's, which is what #291 makes true of the jar itself. The task copies the jar unchanged once the jar
  registers the visitor.

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

JDK 25 (the CI job's JDK) or 21; the specs need the network (the plugin portal and Maven Central) and take a few minutes.

```
./gradlew :test-suite-build:buildToolsTest
```

`build` and `check` do not run the specs (a sample build per spec is slow, and the graphql-java legs do not change what
they prove); `check` runs the license header check. The task is up to date never: it publishes the library first.
`build/reports/build-tools/outcomes.txt` lists the recorded outcomes.
