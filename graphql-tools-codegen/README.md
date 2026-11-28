# micronaut-graphql-tools-codegen

Gradle project `:micronaut-graphql-tools-codegen`, folder `graphql-tools-codegen/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-codegen`.

## Purpose

The code generator of the library, as a plain Java library: the Gradle plugin and the Maven plugin map their configuration one
to one onto it and add only wiring. This module holds the entry point, the options model, the diagnostics, the schema loader and
the intermediate representation (IR), including the operation IR of client mode. The Java emitter (`JavaEmitter`, behind the package-private `Emitter` interface) writes the
enums, the object models (records) and the name constants of the IR; the other languages and the bean model style, the inputs,
interfaces, unions and contracts produce no source file yet. A record that implements an interface or belongs to a union names it, so
the output compiles only once the interface emission exists. The type mappings file is written for every run that has no errors: it checks the options, loads the schema files,
builds the IR and reports what is wrong.
Each package that holds a generated type gets a `package-info.java`.

## Responsibility

- `GraphQLCodeGenerator`: `GraphQLCodeGenerator.builder().withSchemaFiles(..).withOutputDirectory(..).withOptions(..).build().generate()`,
  which returns a `GenerationResult(files, diagnostics)`. It validates the options, loads the schema files and builds the IR
  (`toIr()` returns it). The schema is given as files, as a directory (`withSchemaDirectory`), or by default as
  `src/main/resources/graphql`.
- `IrBuilder` (package `schema`; `fromDirectory`, `fromFiles`, `fromSources`, each with an `OperationInput` overload for client mode): the two steps that turn schema text and `Options` into an `IrResult(ir,
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
| `operationIncludes`, `operationExcludes` | list of globs | `**/*.graphql`; none. Client mode only: the operation documents of a directory |

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
reference (`TypeRef`) keeps its nullability exactly; primitive or boxed is a rule of the emitter, except that a `Field` has a `boxed` flag (below). A reference is a `Builtin`, a `Mapped` external class
(`typeMappings`, by SDL name or by `Type.field`, which replaces the named type of that field inside any list), a `Reference` to a generated
type, or a `ListOf`. An interface that implements an interface keeps it in `implemented`. A sealed interface lists its `members`
(the object types and interfaces that implement it, or the members of a union) and is open when it has no member or when a member is
mapped to an external class. A model lists the unions it belongs to in `memberOf`.
A field that implements an interface field follows two rules, so that the generated types compile: a list whose element type the
implementation narrows (`[Node!]!` in the interface, `[Folder!]!` in the object) keeps the element type of the interface in the IR,
because Java generics are invariant and the generator writes no wildcards, and the diagnostic `narrowed-list-field` says so; a non-null
`Int`, `Float` or `Boolean` that implements a nullable one has `boxed` set, because a primitive return cannot override a boxed one. A single
narrowed value (`Node` in the interface, `Folder` in the object) is left alone, because a Java accessor may return a subtype. The root types are not models: each field of
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
| `operation-file-unreadable` | error | Client mode: an operation document or directory does not exist or cannot be read |
| `operation-syntax-error` | error | Client mode: an operation document is not valid GraphQL |
| `no-operation-documents` | error | Client mode: no document is given, none matches `operationIncludes` and `operationExcludes`, or the documents define no operation |
| `duplicate-operation` | error | Client mode: two operations have one name; the message names both places |
| `anonymous-operation` | error | Client mode: an operation has no name, which names its request class |
| `duplicate-fragment` | error | Client mode: two fragments have one name |
| `operation-invalid` | error | Client mode: the assembled document is not valid against the schema (graphql-java validation, the same as the compile-time checks), or the operation spreads a fragment that no document defines |
| `client-schema-invalid` | error | Client mode: the schema cannot be built to validate the operations |
| `reserved-typename-alias` | error | Client mode: a field is aliased to `__typename`, which the injection of the type name reserves |
| `document-too-large` | error | Client mode: the assembled document is longer than a string constant of a class file (65,535 bytes) |
| `fragment-interface-skipped` | warning | Client mode: a result model keeps the fields of a fragment it spreads but does not implement the fragment's interface (see below) |
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
| `narrowed-list-field` | warning | A field implements an interface field of a list type with a narrower element type; the generated type keeps the element type of the interface, because Java generics are invariant |
| `interface-field-conflict` | error | Two interfaces that a type implements declare one field with a different list element type, or one as a primitive-capable non-null scalar and the other as a nullable scalar, so no single Java type implements both |
| `dependency-type-mappings` | warning | The mappings of a dependency conflict with another's, are unreadable or are not class names |
| `unused-type-mapping` | warning | A `typeMappings` entry names no type or field of the schema |
| `unused-interface-type` | warning | An `interfaceTypes` entry names no object type of the schema |

The option diagnostics (`unknown-option`, `invalid-option-value`, `missing-option`, `invalid-option-combination`) are described under the
options above. A keyword or `Object` method name is an error until a renaming rule exists; there is no renaming option yet.

**Models.** With `modelStyle` `RECORD` (the default), the Java emitter writes one record for each object type that is not a root type and
that `interfaceTypes` does not select, in `modelPackage`. The record has `@Generated` (no date), `@Introspected`, the description as Javadoc
and `@Deprecated` for a deprecated type or field, and `@GraphQLType(name = "<SDL name>")` when a prefix or suffix makes the generated name
differ from the SDL name. It carries no serialization annotation: the library reads a server model through its introspection.

| Rule | Result |
| --- | --- |
| Components | The fields in schema order, the fields of an extension after those of the base definition. A field with arguments is not a component: a type contract resolves it |
| `ID`, `String` | `String` |
| `Int`, `Float`, `Boolean` | `int`, `double`, `boolean` for a non-null value in a component; `Integer`, `Double`, `Boolean` for a nullable one, and for a non-null field with `boxed` set (it implements a nullable interface field) |
| A list | `java.util.List` of its element, with no wildcard; the elements are always reference types; no `Set` and no array |
| A nullable position | The `nullableAnnotation` as a type annotation on the component and on each nullable list element (`@Nullable List<@Nullable String>`); a primitive is never annotated. An empty `nullableAnnotation` writes none |
| An enum, input, model or interface | The generated class |
| A mapped type or scalar | The class of `typeMappings` |
| Interfaces and unions | `implements` lists the interfaces of `implemented`, then the unions of `memberOf` |
| Docs | The description is the Javadoc of the record; a component with a description or a deprecation gets a `@param` line (a record component has no Javadoc of its own), and a deprecation reads `Deprecated: <reason>` |
| JSpecify | When `nullableAnnotation` is in `org.jspecify.annotations`, the `package-info.java` of the model package is `@NullMarked` |

`modelStyle` `BEAN` writes no model yet.

**Name constants.** With `nameConstants` other than `NONE`, the rules emit name classes in `namesPackage`. Each object, interface and input
type gets one class `<Type>Names` (or `<generated name>Names` with a prefix or suffix), with the constant `TYPE` holding the SDL type name and
one constant for each field, so that a constant change recompiles only that type's users. The root types get one class each too. The
names of the enums, unions and scalars, and under `TYPES` the names of every type, are constants of the class `GraphQLTypeNames`. A constant
holds its SDL name; its identifier is the name in upper case with an underscore before each word (`fooBar` gives `FOO_BAR`, and
`HTTPStatus` gives `HTTP_STATUS`). Two names of one class with the same identifier are a `name-collision` error that names both
schema locations, and so is a field named `type` in a type, which would clash with `TYPE`. Argument names have no constants yet.

## Client mode and the operation IR

With `mode` `CLIENT` the generator reads operation documents as well as the schema and fills the IR nodes of the package
`io.micronaut.graphql.tools.codegen.ir.client`: `OperationDocument`, `Variable`, `SelectionSet` (with `SelectedField`,
`SelectedArgument`, `InlineFragment` and `FragmentSpread`) and `FragmentDefinition`. Client mode lists only the enums, inputs and
custom scalars that an operation reaches (through its variables and the fields it selects), so a custom scalar that no operation uses
needs no mapping; it has no resolver contract and no name constants. The Java emitter for client models writes these nodes in a later
change; until then client mode emits the enums.

**Operation documents.** The documents come from a directory (`withOperationDirectory`, or, without any operation input, the schema
directory, where the compile-time checks look for them), from files (`withOperationFiles`) or from text (`withOperationSources`, or the
`OperationInput` argument of `IrBuilder`); the three are alternatives. The files of a directory are those whose path relative to it matches
a glob of `operationIncludes` (default `**/*.graphql`) and no glob of `operationExcludes`, with the globs of `includes` and `excludes`.
Files and text are used as given. Every form reads the documents in the order of their relative paths in UTF-8 bytes, an operation is
in the order of its document, and the same documents in any order give the same IR. An operation needs a name, which names its request
class; an operation or fragment name that two places define is an error.

**One document for the generator and the checks.** For each operation the generator assembles the document as the compile-time checks of
the processor do, and a test compares the two. The operation and the fragments it uses, transitively, become one document that
graphql-java validates against the schema (`operation-invalid` lists what it reports); `__typename` is then put first in every selection
set of an interface or a union that does not select it (the polymorphic result needs it to choose the member, and an alias of
`__typename` is the error `reserved-typename-alias`); the document is printed compactly, the operation first and then its fragments in the
order of their names. `OperationDocument.documentText` is that text and `documentHash` is the SHA-256 of its UTF-8 bytes as 64
lower-case hex digits. The generator builds the schema that validates the documents on a thread with a large stack, as the checks do,
so a long chain of type references is a diagnostic and not a crash. The generator does not call the code of the checks, because this module depends on no other module of the repository; `ClientProcessorEqualitySpec` runs the checks of `graphql-tools-binding` over the same files and compares the text, the hash, the variables and the payload, so a change to the rules is made in both places. `payload` is `FIELD` when the operation selects one root field by
its response key (through fragments), and `DATA` otherwise.

**Variables and presence.** A `Variable` has the type of its declaration, its default value in GraphQL syntax and a `Presence`, which
holds for `InputField` too, in every mode: `REQUIRED` for a non-null type without a default, `OMITTABLE` for a non-null type with a
default (the key may be left out and is never `null`), and `TRI_STATE` for a nullable type, with or without a default, where a left-out key, an explicit `null`
and a value mean three things (an argument that a variable fills is left out when the variable is not provided, and the default of the
argument or of the variable then applies). A generated request type shows `TRI_STATE` with the presence representation that the server
inputs use (`ArgumentValue`) and serializes it so that a left-out key is not written.

**Selections.** `SelectionSet` keeps the fields, the inline fragments and the fragment spreads of the assembled document, each with its
directives. A `SelectedField` has its alias, the schema type and its arguments as the document writes them; a field with a selection
set refers to its schema type by name, because its generated type belongs to the result models.

**Result models.** The result models are ordinary `ModelType` and `AbstractType` nodes, derived from the selection sets and named by
the path from the operation (with `typeNamePrefix` and `typeNameSuffix` around each name). For the operation `GetUser` the request is
`GetUser`, its variables `GetUserVariables` and, when the payload is `DATA`, the `data` object `GetUserData`; a field below `data`
or a root field of a `FIELD` payload is named by the path of its parent and its response key (`GetUserUser`, `GetUserUserPosts`).
The components of a record are named by the response key, so the name of the property in the response is the name of the component;
the `sdlName` of a field is the name in the schema. The `sdlName` of a result model is the schema type its selection is on.

- A selection set on an object type is one record with the fields it selects, merged by response key, through inline fragments and
  spreads that apply to the type.
- A selection set on an interface or a union is a sealed `AbstractType` (the interface carries the fields selected on the abstract type,
  `__typename` first), one record for each object type that a type condition of the selection singles out, in schema order, and a record
  `<path>Other` for every other object type, so a response with a type the client does not know still decodes. The records are the
  `members` of the sealed interface; a record is in `implemented` of the interface, or in `memberOf` when the type is a union. The
  `sdlName` of a member is the object type, which the emitter matches with `__typename`, and the `Other` record has the `sdlName` of the
  abstract type.
- A named fragment is an `AbstractType` of the kind `INTERFACE` with the same names as its `FragmentDefinition`, and every record that
  spreads it implements it, so a function can take any record of the fragment. A fragment that selects a nested object gets its own types
  for it, named by the path from the fragment, and a record that selects exactly that shares them. Java generics are invariant, so a record implements the
  interface of a fragment only when each of its fields has the same type there, and a field with a selection set selects exactly what the
  interface selects; when it does not, the record keeps all the fields, does not implement the interface, and the warning
  `fragment-interface-skipped` says which field differs. For the same reason a field with a selection set that a record of an abstract
  selection extends is not declared on the sealed interface.
- A field that carries `@skip` or `@include`, or sits in an inline fragment or a spread that does, is absent from the response when it is
  skipped even when its schema type is non-null, so its type is nullable and an emitter never turns it into a primitive that reads
  `0` or `false`. A field that another selection of the same record always selects stays non-null. The same holds below a field that is selected twice, once always and once under `@include`: the fields of the second selection can be missing from the object. An introspection field at the root (`__schema`, `__type`) is the error `operation-invalid`; `__typename` at the root is a `String`. The field keeps the directives, and so does the
  `SelectedField`.
- A leaf field is a `Builtin`, a `Mapped` class (`typeMappings`, also `Type.field`, the same as the server) or a reference to an enum that the
  generator emits.

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.codegen` | Public API | `GraphQLCodeGenerator`, `Options`, its enums, `OptionsResult`, `GenerationResult`, `GeneratedFile`, `Diagnostic` and `Origin` |
| `io.micronaut.graphql.tools.codegen.schema` | Public API | `IrBuilder`, `IrResult`, `SchemaSource`, `OperationInput`, `SchemaFileSelector`, `ScalarPresets` and `TypeMappingsFile`; the loader and the rules are package-private |
| `io.micronaut.graphql.tools.codegen.ir` | Public API | The IR nodes (`Ir`, `ModelType`, `InputType`, `EnumType`, `AbstractType`, `ScalarType`, `ResolverContract`, `Field`, `Argument` and the rest) and `IrSnapshot` |
| `io.micronaut.graphql.tools.codegen.ir.shared` | Public API | The pieces of the nodes: `Names`, `Docs`, `AppliedDirective`, `TypeRef`, `BuiltinScalar`, `Coordinate`, `Operation`, `Presence` and `Wrapper` |
| `io.micronaut.graphql.tools.codegen.ir.client` | Public API | The nodes of client mode: `OperationDocument`, `Variable`, `Payload`, `SelectionSet`, `SelectedField`, `SelectedArgument`, `InlineFragment`, `FragmentSpread` and `FragmentDefinition` |

## Who uses it

The Gradle plugin and the Maven plugin of the library, and any build step that generates sources from a schema. An
application does not depend on it.

## Public and internal

- Everything in the packages above that is `public` is the API, and `api/micronaut-graphql-tools-codegen.api`
  is its reviewed snapshot (`apiCheck`). The helpers of the string form, the validation, the loader and the rules are package-private.
- The API names no Gradle, Maven, Micronaut or graphql-java type.

## Relation to other modules

- Depends on graphql-java (`api`, used for the parser, and in client mode to build the schema and validate the operations as the compile-time checks do), on the Micronaut sourcegen generator (`implementation`,
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
`src/test/resources/golden/ir-sample-default` and `ir-sample-styled`. Client mode: `ClientOperationSpec` covers the operations, the
presence, the skip and include rules, the abstract selections and the fragments; `ClientDocumentsSpec` the discovery of the documents and
their diagnostics; `ClientIrGoldenSpec` the IR text snapshot of the schema `src/test/resources/schema/client` (`ir-client-default`); and
`ClientProcessorEqualitySpec` the equality of the document and the hash with the assembled document of the compile-time checks.
`checkNoBuildToolOrRuntimeDependency` runs as part of `check`.
