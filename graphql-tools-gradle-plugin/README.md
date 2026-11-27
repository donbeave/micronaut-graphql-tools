# micronaut-graphql-tools-gradle-plugin

Gradle project `:micronaut-graphql-tools-gradle-plugin`, folder `graphql-tools-gradle-plugin/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-gradle-plugin`, plugin id `io.micronaut.graphql.tools`.

## Purpose

The Gradle plugin of the code generator: a thin adapter that maps the `graphqlTools { codegen { } }` extension onto the
options of `micronaut-graphql-tools-codegen`, runs the generator in an isolated worker, and passes the schema directories to
the compile-time processor. It has no compile dependency on the Micronaut Gradle plugin, so it works in a project that does not apply it.

```kotlin
plugins {
    java
    id("io.micronaut.graphql.tools")
}

graphqlTools {
    schemaDirs.from("src/main/resources/graphql")
    codegen {
        packageName = "example"
    }
}
```

The Groovy DSL is the same: `codegen { packageName = 'example' }`. Gradle 8.14 and the current 9.x are supported.

## Responsibility

- `GraphQLToolsPlugin`: adds the extension, the configurations, one task per spec and the source set wiring.
- `GraphQLToolsExtension`: `graphqlTools { }` with `version`, `addRuntimeDependency`, `schemaDirs` and the named specs.
  `codegen { }` configures the spec `main`; `codegen("client") { }` creates or configures another spec, which has its own task
  `generateClientGraphQL` and its own output directory. The task of `main` is `generateGraphQL`.
- `CodegenSpec`: the options of one generation. Each option has the name of a component of the generator's `Options` record
  and the string form the record has (`Property<String>` for strings and enums, `Property<Boolean>`, `ListProperty<String>`,
  `MapProperty<String, String>`); an option that is not set has the default of the generator. A spec test reads the
  `Options` record and fails when a component has no property or a property has no component. The spec also has
  `schemaDirs` (default `graphqlTools.schemaDirs`, which defaults to `src/main/resources/graphql`), `outputDirectory`
  (default `build/generated/sources/graphqlTools/<name>`) and `resourcesDirectory`.
- `GenerateGraphQLTask`: `@CacheableTask`. Its inputs are the schema directories (relative path sensitivity; the worker selects the files with the
  generator's `SchemaFileSelector`, so `includes` and `excludes` follow the generator's globs), the options in their string form and the generator class path; its output is the output directory. It
  deletes and recreates the output directory before every run, so a type that the schema no longer has leaves no stale source.
  A file that the generator writes is added to the main source set of the language: the Java source set, the Groovy source set or
  the Kotlin source set (the one that the Kotlin plugin adds).
- Type mappings of dependencies: the resolvable configuration `graphqlToolsDependencyClasspath` extends `implementation` and `compileOnly`
  and asks for the jar variant (Gradle's `compileClasspath` of a project dependency is a class directory without its resources,
  which would hide a mappings file). The task has it as `@Classpath` input `dependencyClasspath`, resolved when the task runs and
  not when the build is configured. The worker gives the jars to the generator (`withDependencies`), which reads
  `META-INF/micronaut-graphql-tools/type-mappings.properties` from each of them below `typeMappings`, so a type that a dependency
  maps needs no `typeMappings` entry. With `typeMappingsFromDependencies = false` the classpath is empty, so the dependencies are
  neither read nor an input of the task.
- The mappings file of the module is written to `resourcesDirectory` (default `build/generated/resources/graphqlTools/<name>`, a
  second `@OutputDirectory`, recreated on every run), which the plugin adds to the resources of the main source set, so
  `processResources` puts it in the jar. It exists only when the module generates or maps a type. Two specs of one project write
  the same path, so only one of them should map types.
- `GenerateAction` and `GenerateParameters`: the worker (`classLoaderIsolation`). The parameters hold files and strings only;
  the worker reads the options with `Options.fromMap`, validates the options, selects the schema files, runs the generator and writes each generated file whose content changed. An
  error diagnostic fails the build with every message, naming the option or the schema file.
- `CompileTimeWiring`, `SchemaDirsArguments`: pass `graphqlTools.schemaDirs` and the project directory to the processor as
  `micronaut.graphql.tools.processor.schema.dirs` and `micronaut.graphql.tools.processor.module.dir`: as `-A` options of `javac`
  (a `CommandLineArgumentProvider` that is an input of `compileJava`), as a system property of the Groovy compiler's JVM, as a `ksp`
  argument and as a `kapt` argument. A compiler is wired only when the processor (`micronaut-graphql-tools-processor`) is
  declared for it, so a project that only generates code gets no unused option. The schema directories are an input of the
  Groovy, KSP and kapt tasks, so a schema edit reruns them.

## The generator is not on any class path of the build

- The module declares the generator `compileOnly`, so the plugin's jar and its published metadata do not bring it.
- The configurations `graphqlToolsCodegen` (a bucket) and `graphqlToolsCodegenClasspath` (resolvable) hold it. The coordinate
  `io.micronaut.graphql.tools:micronaut-graphql-tools-codegen:<version>` is added lazily from `graphqlTools.version`.
- The worker loads the generator and its dependencies from `graphqlToolsCodegenClasspath` in a class loader of its own.
  An application's `compileClasspath` and `runtimeClasspath` never hold it.
- `graphqlTools.version` defaults to the version of the plugin, which is written to a resource at build time. With
  `addRuntimeDependency` (default `true`) the plugin adds `micronaut-graphql-tools` (mode `SERVER`) or
  `micronaut-graphql-tools-client` (mode `CLIENT`) of that version to `implementation`.

## Build and test

- `src/test`: Spock unit tests (`./gradlew :micronaut-graphql-tools-gradle-plugin:test`, part of `check`): option parity with the
  `Options` record, the string form, the default version, the configurations and the language inference.
- `src/functionalTest`: Gradle TestKit scenarios on the lowest supported Gradle (8.14) and the current one
  (`./gradlew :micronaut-graphql-tools-gradle-plugin:functionalTest`; it publishes the generator to `build/repo` and needs the
  network; the CI job `build tools` runs it, `check` does not). They cover the Kotlin and Groovy DSL, the generator being absent
  from the build-script and application class paths, the default version, up-to-date and build-cache behavior (second run
  `UP-TO-DATE`, edit reruns, clean build `FROM-CACHE`), the stale output, the diagnostics, the type mappings of a dependency jar and of a project dependency (and that the option off reads
  none), and the compile-time wiring for javac,
  Groovy, KSP and kapt. Gradle 8.14 does not run on JDK 25 or newer, so on those JDKs its scenarios use a JDK 21 for the
  daemon (`JAVA_HOME_21_X64` or a toolchain) and are skipped when there is none.

## Who uses it

Applications and libraries that generate Java, Kotlin or Groovy sources from GraphQL schema files in a Gradle build.

## Public and internal

The package `io.micronaut.graphql.tools.gradle` is public API. The generator version is pinned by the plugin version, and
`api/micronaut-graphql-tools-gradle-plugin.api` is the reviewed snapshot of the signatures.

## Relation to other modules

- Compiles against `micronaut-graphql-tools-codegen` (`compileOnly`) and resolves it at build time in the consuming project;
  the plugin's jar and POM do not depend on it.
- Wires the options of `micronaut-graphql-tools-processor` and adds `micronaut-graphql-tools` or `micronaut-graphql-tools-client`
  as the runtime dependency of the generated code.
- Its version is the `projectVersion` in `gradle.properties`.
