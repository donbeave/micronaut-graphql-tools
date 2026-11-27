# micronaut-graphql-tools-codegen

Gradle project `:micronaut-graphql-tools-codegen`, folder `graphql-tools-codegen/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-codegen`.

## Purpose

The code generator of the library, as a plain Java library: the Gradle plugin and the Maven plugin map their configuration one
to one onto it and add only wiring. This module holds the entry point, the options model and the diagnostics. The schema
model, the rules and the emitters are added to it by the generator phases, so this version produces no file yet: it checks the
options and the schema files and reports what is wrong.

## Responsibility

- `GraphQLCodeGenerator`: `GraphQLCodeGenerator.builder().withSchemaFiles(..).withOutputDirectory(..).withOptions(..).build().generate()`,
  which returns a `GenerationResult(files, diagnostics)`. It validates the options and parses each schema file with the
  graphql-java parser.
- `Options`: one immutable record, the single source of truth for the options, with its Javadoc, defaults and builder. Schema
  files and the output directory are arguments of the generator builder, not options.
- The string form of the options, `Options.toMap()` and `Options.fromMap(Map<String,String>)`. A Gradle worker that runs the
  generator in its own class loader cannot take the enums or records of the plugin's class loader, so the plugin passes the
  map. `toMap()` lists only the values that differ from the default, in key order; `fromMap(toMap(options))` equals `options`.
- `Options.validate()` and `Options.fromMap`: both return `Diagnostic`s that name the option, and neither throws for an
  invalid value.
- The `checkNoBuildToolOrRuntimeDependency` task, part of `check`: it fails when a main classpath holds a Gradle, Maven or
  Micronaut runtime dependency, or another module of this repository.

Not in this module: the Gradle plugin and the Maven plugin (`graphql-tools-maven-plugin`), the schema loader and the IR, the emitters and the golden
harness.

## Options

The names are in GraphQL vocabulary and do not reuse the options of the compile-time processor. The Javadoc of `Options` is the
reference; this is the summary.

| Option | String form | Default |
| --- | --- | --- |
| `language` | `JAVA`, `KOTLIN` or `GROOVY` | `JAVA` |
| `mode` | `SERVER` or `CLIENT` | `SERVER`; `CLIENT` generates Java only |
| `packageName` | string | required (empty, and `validate()` reports it) |
| `modelPackage`, `resolverPackage`, `namesPackage` | string | empty: `<packageName>.model`, `.resolver` and `.names` |
| `generatedAnnotation` | class name, empty for none | `javax.annotation.processing.Generated` |
| `modelStyle` | `RECORD` or `BEAN` | `RECORD` |
| `interfaceTypes` | list of SDL object type names | empty |
| `typeNamePrefix`, `typeNameSuffix` | string | empty |
| `nullableAnnotation` | class name, empty for none | `io.micronaut.core.annotation.Nullable`; `org.jspecify.annotations.Nullable` in client mode |
| `rootContracts` | `true` or `false` | `true` |
| `contractNameTemplate` | string with `{Field}` and `{Operation}` | `{Field}{Operation}Resolver` |
| `kotlinSuspend` | `true` or `false` | `false`; needs `language` `KOTLIN` |
| `subscriptionReturnType` | class name | `org.reactivestreams.Publisher` |
| `typeMappings` | map from an SDL name or `Type.field` to a class | empty |
| `nameConstants` | `NONE`, `TYPES` or `FIELDS` | `FIELDS` |
| `includes`, `excludes` | list of globs | `**/*.graphqls` and `**/*.graphql`; none |
| `commentsAsDescriptions` | `true` or `false` | `false`; must match the runtime setting |

An unmapped custom scalar is always a diagnostic, never an option: there is no silent fallback to `String`.

The string form of a list is `name.size` followed by `name.0`, `name.1` and so on (an empty list is `name.size=0`); the
string form of a map is `name.<key>`, for example `typeMappings.Date=java.time.LocalDate`. An absent key has its default.
`fromMap` reports an unknown key, an invalid enum constant (the message lists the valid ones), a number or a boolean that does
not parse, and an incomplete list, each as a diagnostic that names the option. `validate()` reports an invalid name, an empty
`includes`, `kotlinSuspend` with a language other than Kotlin, and client mode with a language other than Java.

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.codegen` | Public API | `GraphQLCodeGenerator`, `Options`, its enums, `OptionsResult`, `GenerationResult`, `GeneratedFile`, `Diagnostic` and `Origin` |

## Who uses it

The Gradle plugin and the Maven plugin of the library, and any build step that generates sources from a schema. An
application does not depend on it.

## Public and internal

- Everything in `io.micronaut.graphql.tools.codegen` that is `public` is the API, and `api/micronaut-graphql-tools-codegen.api`
  is its reviewed snapshot (`apiCheck`). The helpers of the string form and of the validation are package-private.
- The API names no Gradle, Maven, Micronaut or graphql-java type.

## Relation to other modules

- Depends on graphql-java (`api`, used for the parser only), on the Micronaut sourcegen generator (`implementation`,
  non-transitive: only its JavaPoet fork is used, and the fork runs with the JDK alone) and on KotlinPoet (`implementation`).
  The Micronaut platform manages the versions of the last two, so none is declared here and the version catalog has no entry
  for them. They stay on the generator classpath of the plugins and never reach an application.
- It does not depend on `graphql-tools`, `graphql-tools-binding` or any other module of this repository, and no Gradle, Maven
  or Micronaut runtime artifact is on its classpath.
- It is published with the other library modules and managed by `graphql-tools-bom`.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-codegen:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17, and on both ends of the graphql-java range
(`-PgraphqlJavaLeg=floor` and `-PgraphqlJavaLeg=ceiling`). `OptionsSpec` covers the defaults, the round trip of the string form
and the diagnostics, `OptionsDocumentationSpec` checks that every option is documented with its default,
and `GraphQLCodeGeneratorSpec` covers the entry point. `checkNoBuildToolOrRuntimeDependency` runs as part of `check`.
