# micronaut-graphql-tools-codegen

Gradle project `:micronaut-graphql-tools-codegen`, folder `graphql-tools-codegen/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-codegen`.

## Purpose

The code generator of the library, as a plain Java library: the Gradle plugin and the Maven plugin map their configuration one
to one onto it and add only wiring. This module holds the entry point, the options model, the diagnostics, the schema loader and
the intermediate representation (IR). The Java emitter (`JavaEmitter`, behind the package-private `Emitter` interface) writes the
enums and the name constants of the IR; the other languages and the models, inputs, interfaces, unions and contracts produce no
source file yet. The type mappings file is written for every run that has no errors: it checks the options, loads the schema files,
builds the IR and reports what is wrong.
Each package that holds a generated type gets a `package-info.java`.

## Responsibility

- `GraphQLCodeGenerator`: `GraphQLCodeGenerator.builder().withSchemaFiles(..).withOutputDirectory(..).withOptions(..).build().generate()`,
  which returns a `GenerationResult(files, diagnostics)`. It validates the options, loads the schema files and builds the IR
  (`toIr()` returns it). The schema is given as files, as a directory (`withSchemaDirectory`), or by default as
  `src/main/resources/graphql`.
- `IrBuilder` (package `schema`; `fromDirectory`, `fromFiles`, `fromSources`): the two steps that turn schema text and `Options` into an `IrResult(ir,
  diagnostics, skippedFiles)`. The loader reads with the graphql-java parser only and merges; the rules lower the merged
  definitions into the IR. The packages `io.micronaut.graphql.tools.codegen.ir` and `ir.shared` hold the IR records and `IrSnapshot`, which prints an
  IR as the text that the golden tests compare.
- `Options`: one immutable record, the single source of truth for the options, with its Javadoc, defaults and builder. Schema
  files and the output directory are arguments of the generator builder, not options.
- The string form of the options, `Options.toMap()` and `Options.fromMap(Map<String,String>)`. A Gradle worker that runs the
  generator in its own class loader cannot take the enums or records of the plugin's class loader, so the plugin passes the
  map. `toMap()` lists only the values that differ from the default, in key order; `fromMap(toMap(options))` equals `options`.
- `Options.validate()` and `Options.fromMap`: both return `Diagnostic`s that name the option, and neither throws for an
  invalid value.
- The `checkNoBuildToolOrRuntimeDependency` task, part of `check`: it fails when a main classpath holds a Gradle, Maven or
  Micronaut runtime dependency, or another module of this repository.

- The golden harness, test code in `io.micronaut.graphql.tools.codegen.golden` (#192): `GoldenFiles` compares the files of a
  case, or an IR text, with `src/test/resources/golden/<case>/`; `Determinism` checks two runs, shuffled schema orders, `\n`
  line endings, no timestamp and a `@Generated` without a date; `InMemoryCompiler` compiles all files of a case in memory
  (`JavaInMemoryCompiler` with javac, `GroovyInMemoryCompiler` with groovyc; a Kotlin emitter adds one `compile` method with
  kotlinc). `./gradlew :micronaut-graphql-tools-codegen:test -PupdateGolden` rewrites the snapshots; review the diff and commit it.
  Keep the 1k-type compile out of the unit suites.


Not in this module: the Gradle plugin and the Maven plugin (`graphql-tools-maven-plugin`), and the emitters.

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
| `typeMappings` | map from an SDL name or `Type.field` to a class | empty, plus the scalar presets below |
| `typeMappingsFromDependencies` | `true` or `false` | `true`; reads the mappings of the dependency jars the caller gives the generator |
| `nameConstants` | `NONE`, `TYPES` or `FIELDS` | `FIELDS` |
| `includes`, `excludes` | list of globs | `**/*.graphqls` and `**/*.graphql`; none |
| `commentsAsDescriptions` | `true` or `false` | `false`; must match the runtime setting |

An unmapped custom scalar is always a diagnostic, never an option: there is no silent fallback to `String`.

## Type mappings

A `typeMappings` key is the SDL name of a scalar, or of an object, enum or input type (the class is an existing one and nothing
is generated for the type), or `Type.field` for a field whose type is a scalar (it replaces the named type of that field inside
any list). A `Type.field` key for a field of another kind is the error `field-mapping-not-scalar`: a GraphQL type has one Java
type, so map the type itself. An object type that is mapped and is a member of a union, or implements an interface, cannot be
generated as a permitted subtype: the union or interface is then a plain interface and the generator reports the warning
`mapped-member`; the application registers the class as a member itself.

A scalar that the library registers at runtime has a preset, so a schema that declares it needs no entry, and the class matches the
runtime `GraphQLScalarMapping`. A preset applies to a scalar only, and an entry of `typeMappings` wins over it. The scalars module
and the generator are checked against each other, so the table follows `StandardScalar`.

| Scalar | Class |
| --- | --- |
| `Long`, `Short` | `java.lang.Long`, `java.lang.Short` |
| `BigDecimal`, `BigInteger` | `java.math.BigDecimal`, `java.math.BigInteger` |
| `JSON` | `java.lang.Object` |
| `Upload` | `io.micronaut.http.multipart.CompletedFileUpload` |
| `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `YearMonth`, `Year`, `Duration`, `Period` | the `java.time` class of the same name |
| `Url`, `UUID`, `Locale`, `Currency` | `java.net.URL`, `java.util.UUID`, `java.util.Locale`, `java.util.Currency` |
| `PositiveInt`, `NegativeInt`, `NonPositiveInt`, `NonNegativeInt` | `java.lang.Integer` |
| `PositiveFloat`, `NegativeFloat`, `NonPositiveFloat`, `NonNegativeFloat` | `java.lang.Double` |

`ScalarPresets.presets()` returns the table. Any other custom scalar needs an entry; the error names it.

**Mappings between modules.** The generator writes `META-INF/micronaut-graphql-tools/type-mappings.properties` for its own output:
one `SdlName=qualified.ClassName` line for each type it generates and each custom scalar it maps, sorted by name, with no date, so
the bytes only change when the mappings do (`GraphQLCodeGenerator.generate()` returns it as a `GeneratedFile`; a build plugin puts it
in a resource directory; both plugins do). A module that depends on it passes the dependency jars or class directories (`withDependencies`, or the
`dependencies` argument of `IrBuilder`; the plugins pass the compile classpath), and with `typeMappingsFromDependencies` on the generator reads that file from each of
them. The order of precedence is the `typeMappings` option, then the dependencies, then the presets. The jars are read in the order of
their paths and the first mapping of a name wins; two jars that map a name to different classes, an unreadable jar and a value that is
no class name are the warning `dependency-type-mappings`.

The string form of a list is `name.size` followed by `name.0`, `name.1` and so on (an empty list is `name.size=0`); the
string form of a map is `name.<key>`, for example `typeMappings.Date=java.time.LocalDate`. An absent key has its default.
`fromMap` reports an unknown key, an invalid enum constant (the message lists the valid ones), a number or a boolean that does
not parse, and an incomplete list, each as a diagnostic that names the option. `validate()` reports an invalid name, an empty
`includes`, `kotlinSuspend` with a language other than Kotlin, and client mode with a language other than Java.

## Schema loader and IR

The IR is the only thing that the emitters see. It is a tree of immutable records, language neutral, and a pure function of the schema
files and the options.

**Loading.** With `fromDirectory`, the files are those under the directory whose path relative to it matches a glob of `includes`
(default `**/*.graphqls` and `**/*.graphql`) and no glob of `excludes`. A glob matches the relative path with `/` separators on every
platform: `*` stops at `/`, `**` does not, `**/` also matches no directory (so `**/*.graphqls` matches a file in the root), `?` is one
character and `{a,b}` is an alternative. The files are sorted by the UTF-8 bytes of their relative path and a path that appears twice is read
once, so the order of the directory listing, or of the files that a caller passes, changes nothing. Definitions keep their order
inside a file. Every `extend` is merged after the base definitions of all files, in file order, so an extension may come before its
base. A file that holds only operations or fragments (client documents) or only comments is skipped and listed in `skippedFiles`; it is
not an error. A schema without a `schema` block has the roots `Query`, `Mutation` and `Subscription` when it defines `Query`, as the
runtime and the compile-time checks decide. This loader does not call the runtime code, because the module depends on no other
module of the library, so a change to those rules is made in both places.

**Nodes.** `ModelType`, `InputType`, `EnumType`, `AbstractType` (an interface, a union, or an object type that `interfaceTypes`
selects), `ScalarType`, `ResolverContract` (`ContractMethod`, `Argument`, `Wrapper`) and the name classes (`NameClass`, with its `NameConstant`s). Each carries the SDL name and the generated
name side by side (`Names`), `Docs` (description and deprecation), the applied directives as a name and string arguments (without
`@deprecated`, which is in `Docs`) and an `Origin` (relative path, line, column) that is never written into generated code. Every type
reference (`TypeRef`) keeps its nullability exactly; primitive or boxed is a rule of the emitter. A reference is a `Builtin`, a `Mapped` external class
(`typeMappings`, by SDL name or by `Type.field`, which replaces the named type of that field inside any list), a `Reference` to a generated
type, or a `ListOf`. An interface that implements an interface keeps it in `implemented`. A sealed interface lists its `members`
(the object types and interfaces that implement it, or the members of a union) and is open when it has no member or when a member is
mapped to an external class. A model lists the unions it belongs to in `memberOf`. The root types are not models: each field of
`Query`, `Mutation` and `Subscription` is a `ResolverContract` named by `contractNameTemplate` in `resolverPackage`, extending
`QueryResolver`, `MutationResolver` or `SubscriptionResolver`. With `kotlinSuspend` a query or mutation method has the wrapper `SUSPEND`;
a subscription method is wrapped in `subscriptionReturnType`. `rootContracts=false` generates none, and the fields of the root types are still validated. An `extend schema` block without a `schema` block adds its operations to the implicit roots (`Query`, `Mutation`, `Subscription`) that the files define. Every list is in schema order.

**Documentation.** The description is the SDL description, or with `commentsAsDescriptions` the `#` comment lines before a definition
(a line that holds only `#` ends them), as graphql-java reads them. Neither JavaPoet nor KotlinPoet escapes comment text, so the loader
escapes it once for every emitter: `*/` and `/*` (Kotlin nests comments), a backslash (a `\u` sequence is read inside comments), `<`, `&` and `@`
become character references such as `&#47;`, and line endings become `\n`.

**Diagnostics.** Every problem is a `Diagnostic` with a stable code, a message that names the schema element and its location
(`path:line:column`), and the origin; one run reports all of them, in a stable order that does not depend on the order of the files given. When a diagnostic is an error the IR is empty.

| Code | Severity | When |
| --- | --- | --- |
| `schema-file-unreadable` | error | A schema file or directory does not exist or cannot be read |
| `no-schema-files` | error | No file under the directory matches `includes` and `excludes` |
| `schema-syntax-error` | error | A file is not valid GraphQL |
| `duplicate-definition` | error | A type, field, argument, enum value or root operation is defined twice; the message names both places |
| `extension-without-base` | error | An `extend` names a type that no file defines |
| `extension-kind-mismatch` | error | An `extend` is of another kind than its base, such as `extend type` of an enum |
| `unknown-type` | error | A field, argument, union, interface or root operation names a type that is defined nowhere and not in `typeMappings` |
| `unmapped-scalar` | error | A custom scalar has no `typeMappings` entry; there is no silent `String` |
| `reserved-name` | error | A type, field, argument or enum value name is a Java keyword (and a Kotlin or Groovy keyword for that language); a generated type or contract name is `record`, `var`, `sealed`, `permits` or `yield`, which Java restricts as type names in every output language |
| `invalid-implements` | error | A type implements a type that is not an interface, such as an object type or an enum |
| `invalid-union-member` | error | A union has a member that is not an object type |
| `reserved-contract-name` | error | `contractNameTemplate` resolves a contract to `Query`, `Mutation` or `Subscription`, the name of a root type |
| `object-method-name` | error | A field is named like a method of `java.lang.Object` (`clone`, `finalize`, `getClass`, `hashCode`, `notify`, `notifyAll`, `toString`, `wait`) |
| `name-collision` | error | Two schema elements, or an element and a generated helper class (`GraphQLTypeNames`, `<Type>Names`), generate the same class, or two name constants of one class have the same identifier; the class comparison folds the case of the path, because two names such as `Foo` and `foo` overwrite each other on a case-insensitive file system |
| `root-type-reference` | error | A field or argument has a root operation type as its type (map it with `typeMappings`) |
| `field-mapping-not-scalar` | error | A `Type.field` entry of `typeMappings` names a field whose type is an object, interface, union, enum or input type |
| `mapped-member` | warning | A union has a member, or an interface an implementing type, that `typeMappings` maps to an external class; the union or interface is not sealed |
| `dependency-type-mappings` | warning | The mappings of a dependency conflict with another's, are unreadable or are not class names |
| `unused-type-mapping` | warning | A `typeMappings` entry names no type or field of the schema |
| `unused-interface-type` | warning | An `interfaceTypes` entry names no object type of the schema |

The option diagnostics (`unknown-option`, `invalid-option-value`, `missing-option`, `invalid-option-combination`) are described under the
options above. A keyword or `Object` method name is an error until a renaming rule exists; there is no renaming option yet.

**Name constants.** With `nameConstants` other than `NONE`, the rules emit name classes in `namesPackage`. Each object, interface and input
type gets one class `<Type>Names` (or `<generated name>Names` with a prefix or suffix), with the constant `TYPE` holding the SDL type name and
one constant for each field, so that a constant change recompiles only that type's users. The root types get one class each too. The
names of the enums, unions and scalars, and under `TYPES` the names of every type, are constants of the class `GraphQLTypeNames`. A constant
holds its SDL name; its identifier is the name in upper case with an underscore before each word (`fooBar` gives `FOO_BAR`, and
`HTTPStatus` gives `HTTP_STATUS`). Two names of one class with the same identifier are a `name-collision` error that names both
schema locations, and so is a field named `type` in a type, which would clash with `TYPE`. Argument names have no constants yet.

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.codegen` | Public API | `GraphQLCodeGenerator`, `Options`, its enums, `OptionsResult`, `GenerationResult`, `GeneratedFile`, `Diagnostic` and `Origin` |
| `io.micronaut.graphql.tools.codegen.schema` | Public API | `IrBuilder`, `IrResult`, `SchemaSource`, `SchemaFileSelector`, `ScalarPresets` and `TypeMappingsFile`; the loader and the rules are package-private |
| `io.micronaut.graphql.tools.codegen.ir` | Public API | The IR nodes (`Ir`, `ModelType`, `InputType`, `EnumType`, `AbstractType`, `ScalarType`, `ResolverContract`, `Field`, `Argument` and the rest) and `IrSnapshot` |
| `io.micronaut.graphql.tools.codegen.ir.shared` | Public API | The pieces of the nodes: `Names`, `Docs`, `AppliedDirective`, `TypeRef`, `BuiltinScalar`, `Coordinate`, `Operation` and `Wrapper` |

## Who uses it

The Gradle plugin and the Maven plugin of the library, and any build step that generates sources from a schema. An
application does not depend on it.

## Public and internal

- Everything in the packages above that is `public` is the API, and `api/micronaut-graphql-tools-codegen.api`
  is its reviewed snapshot (`apiCheck`). The helpers of the string form, the validation, the loader and the rules are package-private.
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
and `GraphQLCodeGeneratorSpec` covers the entry point. `SchemaLoaderSpec` covers discovery, globs, sorting, de-duplication,
skipped files and the merge errors; `IrStructureSpec` the nodes; `IrDiagnosticsSpec` each failure with its text and location;
`IrDeterminismSpec` the identical IR for any file order and a schema of 1,000 types and 20,000 fields; `NodeTextSpec` the escaping; and
`IrGoldenSpec` compares the IR text snapshots of the sample schema through the golden harness, in
`src/test/resources/golden/ir-sample-default` and `ir-sample-styled`.
`checkNoBuildToolOrRuntimeDependency` runs as part of `check`.
