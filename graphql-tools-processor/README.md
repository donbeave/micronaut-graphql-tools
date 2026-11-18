# micronaut-graphql-tools-processor

Gradle project `:micronaut-graphql-tools-processor`, folder `graphql-tools-processor/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-processor`.

## Purpose

The compile-time checks. The annotation processor reads the schema files while `javac` compiles the application, and
fails the build on drift that the startup would otherwise report, for example an object type that nothing reachable
returns. Each error names the schema coordinate and the schema file line. The guide page is `src/main/docs/guide/compileTime.adoc`.

Today the processor is a JSR-269 annotation processor (`GraphQLSchemaBindingProcessor`, registered in
`META-INF/services/javax.annotation.processing.Processor`). It is being replaced by a `TypeElementVisitor` in the package
`io.micronaut.graphql.tools.visitor` (#291). The JSR-269 code is deleted by #144 after the visitor reaches parity. The
visitor is not in the tree yet.

## Responsibility

- Read the schema files and the resolver beans of one compilation (`processing/`: `SchemaFiles`, `ResolverBeans`,
  `SchemaChecks`, `BindingChecks`).
- Read the processor options that are passed as `-A` compiler arguments (`ProcessorOptions`).
- Report the findings as compiler errors and, when the checks pass, write the binding manifest to the class output.
- Not in this module: the runtime library and the Micronaut bean lookup. The processor must not depend on the runtime:
  the `checkNoRuntimeDependency` task fails `check` when a main classpath contains `graphql-tools`.

## Who uses it

Application developers, who add it to the annotation processor path next to Micronaut's processor (`annotationProcessor`,
or `kapt` for Kotlin). `test-suite-java` does this in its build. Maintainers use the binding corpus in its tests.

## Public and internal

Application code does not import anything from this module. Its classes are used by `javac` only, through the processor's
service registration. The `processing` package is not annotated `@Internal` (one file is); it is internal by use, and it
is removed by #144.

## Relation to other modules

- Depends on `graphql-tools-binding` (`implementation`): the schema reader (`SchemaSources`, `SchemaSourceException`).
- Depends on `graphql-java` (`implementation`).
- Tests depend on `graphql-tools` (`testImplementation`): the binding corpus runs each case through the runtime binder and
  the compile-time binder and compares their verdicts.
- Used by `test-suite-java`, which puts the processor on its `annotationProcessor` path. `test-suite-kotlin` and
  `test-suite-groovy` do not use it.
- `graphql-tools-bom` manages its version.

## Build and test

The module compiles for Java 21 (`micronautBuild.javaVersion = 21`), so it does not run on JDK 17. Use JDK 21 or 25:

```
./gradlew :micronaut-graphql-tools-processor:check
```

`check` runs the unit tests, the binding corpus (`BindingCorpusSpec`, about 70 seconds on the default graphql-java
leg), `ProcessorOptionsDocsSpec` (it fails when a processor option is not named in the options page) and
`checkNoRuntimeDependency`. The floor and ceiling legs skip the corpus.

- The corpus is data in `src/test/resources/corpus`; its README says how to add a case.
- `./gradlew :micronaut-graphql-tools-processor:corpusUpdate` rewrites the expected files of the corpus from what the
  binders say. Review the diff.
- `./gradlew :micronaut-graphql-tools-processor:corpusKotlin` compiles the Kotlin cases in one TestKit build. It needs the
  network and is not part of `check`.
