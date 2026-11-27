# micronaut-graphql-tools-bom

Gradle project `:micronaut-graphql-tools-bom`, folder `graphql-tools-bom/`. It has no sources and no tests.

## Purpose

The bill of materials (BOM) for the published artifacts. It pins one version of each artifact, so that an application
that imports the BOM uses matching versions of the library, the binding module, the standard scalars module, the Micrometer module, the OpenTelemetry module, the upload module, the security module, the testing module, the client module, the code generator module, the Gradle plugin module and the compile-time processor.

## Responsibility

- Manage the versions of `micronaut-graphql-tools`, `micronaut-graphql-tools-binding`, `micronaut-graphql-tools-processor`,
  `micronaut-graphql-tools-scalars`, `micronaut-graphql-tools-micrometer`, `micronaut-graphql-tools-opentelemetry`, `micronaut-graphql-tools-upload`, `micronaut-graphql-tools-security`, `micronaut-graphql-tools-testing`, `micronaut-graphql-tools-client`, `micronaut-graphql-tools-codegen` and `micronaut-graphql-tools-gradle-plugin` (the generated POM lists these twelve in `dependencyManagement`).
- Nothing else: no code, no dependencies of its own. The build applies the Micronaut BOM convention
  (`io.micronaut.build.internal.bom`) from `micronaut-build`.

## Who uses it

Library users who want one version for all the artifacts.

## Public and internal

The BOM is public. It lists the twelve artifacts above; the test suites, the benchmarks and `test-suite-tck` are not
in it.

## Relation to other modules

- Depends on nothing in this repository.
- Its version is the `projectVersion` in `gradle.properties`.
- The modules it manages are `graphql-tools`, `graphql-tools-binding`, `graphql-tools-processor`, `graphql-tools-scalars`, `graphql-tools-micrometer`, `graphql-tools-opentelemetry`, `graphql-tools-upload`, `graphql-tools-security`, `graphql-tools-testing`, `graphql-tools-client`, `graphql-tools-codegen` and `graphql-tools-gradle-plugin`.

## Build and test

Generate the POM and inspect the managed versions:

```
./gradlew :micronaut-graphql-tools-bom:generatePomFileForMavenPublication
```

The POM is written to `graphql-tools-bom/build/publications/maven/pom-default.xml`.
