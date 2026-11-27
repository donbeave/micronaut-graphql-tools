# micronaut-graphql-tools-maven-plugin

Gradle project `:micronaut-graphql-tools-maven-plugin`, folder `graphql-tools-maven-plugin/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-maven-plugin`, goal prefix `graphql-tools`.

## Purpose

The Maven plugin of the code generator: the goal `graphql-tools:generate` maps a `<configuration>` one to one onto the options of
`graphql-tools-codegen`, writes the generated sources, and adds the source directory of the language to the project. The generator
produces no source file yet (it checks the options and the schema files), so the plugin writes what the generator returns and the
build wiring is already final.

## Responsibility

- `GenerateMojo`: `@Mojo(name = "generate", defaultPhase = GENERATE_SOURCES, threadSafe = true)`. Every component of `Options` is a
  parameter of the same name and default, and its value is the string form of the option (`Options.fromMap`). `schemaDirectory`
  (default `src/main/resources/graphql`), `outputDirectory` (default `target/generated-sources/graphql-tools`) and `skip` belong to
  the plugin. The property of a parameter is the dotted lowercase name under `micronaut.graphql.tools.codegen.`, for example
  `micronaut.graphql.tools.codegen.package.name`. A list or a map has no property.
- The generated files are written under `<outputDirectory>/<language>` (`java`, `kotlin` or `groovy`) and that directory is added
  with `project.addCompileSourceRoot`. The Kotlin compiler plugin lists the directory in `sourceDirs`, and GMavenPlus lists it in
  `sources` (the guide shows both).
- The schema files: selected by `SchemaFileSelector` of the code generator, so the globs follow the generator's rules (`[` and `]` are literal,
  `**/` also matches no directory) and the files come in the order of their relative path. The options are validated before the selection,
  so an invalid configuration fails even when no schema file matches.
- `GeneratedSources`: the incremental state. Maven has no up-to-date engine, so the manifest `.graphql-tools-generated` in the output
  directory holds the fingerprint of the run (schema paths and content, the canonical string form of the options, the version of
  the plugin and so of the generator) and the files it wrote. An unchanged run is skipped; a changed input or a new version
  regenerates; a file that the manifest lists and the run no longer emits is deleted; a file is written only when its bytes differ.
- `META-INF/maven/plugin.xml`: written by hand (the plugin does not use `maven-plugin-plugin`), with `@version@` replaced by the
  build. `PluginDescriptorSpec` compares it with the `@Parameter` fields of the mojo and with the components of `Options`.

Not in this module: the generator (`graphql-tools-codegen`), the Gradle plugin, and the option parity test across the plugins (#194).

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.maven` | Public API | `GenerateMojo`; the other types are package-private |

## Who uses it

A Maven build that generates sources from a schema. The plugin is a build tool: an application does not depend on it.

## Public and internal

- `GenerateMojo` is the only public type; its parameters are the configuration of the plugin, and `api/micronaut-graphql-tools-maven-plugin.api`
  is the reviewed snapshot (`apiCheck`).
- The Maven API (`maven-plugin-api`, `maven-core`, the annotations) is compile-only: the running Maven provides it, so the published
  POM names only `graphql-tools-codegen`. The plugin is compiled against Maven 3.9.11 and runs on Maven 3.9 and later.

## Relation to other modules

- Depends on `graphql-tools-codegen` (`implementation`). The generator, graphql-java, the JavaPoet fork and KotlinPoet are in the
  class loader of the plugin, never on an application's class path.
- It is published with the other library modules and managed by `graphql-tools-bom`.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-maven-plugin:check
./gradlew :test-suite-build:buildToolsTest --tests '*MavenPluginSampleTest'
```

The first command compiles with `--release 17`, so it also runs on JDK 17. `GenerateMojoSpec` binds a pom `<configuration>` to the mojo
with the Maven plugin testing harness (so it reads the hand-written `plugin.xml`) and asserts the options, the defaults and the source
root of each language; `IncrementalRunSpec` and `GeneratedSourcesSpec` cover the skip, the regeneration and the
deletion of a stale file, and `SchemaFileSelectorSpec` of the code generator covers the file selection; `PluginDescriptorSpec` is the parity of the descriptor. The second command needs `mvn` and the network: it
builds the samples `maven-plugin-java`, `maven-plugin-kotlin` and `maven-plugin-groovy` of `test-suite-build` with the published plugin.
