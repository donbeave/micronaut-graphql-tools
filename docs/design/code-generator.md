# Code generator design

Status: proposed, for review. Nothing in this document is implemented yet. It settles the shape of the generator before
the `graphql-tools-codegen` module is created (#193), so that the SDL loader and the IR (#16), the golden harness (#192),
the emitter interface (#191) and the Kotlin and Groovy emitters (#187, #189) are built on the same decisions.

The generator turns a GraphQL schema into Java, Kotlin or Groovy sources that bind to the library by construction. A
service gets contracts, models, inputs and enums without hand-written glue. The same generator has a client mode.

## What is already settled

These points come from the scope of code generation version 1 and from merged library work. This document records them
and does not reopen them.

- One generator library, one Gradle plugin and one Maven plugin share one option set. The plugins map the options one to
  one and add only wiring.
- Java, Kotlin and Groovy are equal for server output. The build order is Java first, then the emitter interface, then
  Kotlin and Groovy in parallel; the order is not a priority order.
- Generated contracts extend the marker interfaces (`QueryResolver`, `MutationResolver`, `SubscriptionResolver`,
  `TypeResolver<T>`). They carry no bean stereotype.
- Inputs are records in every case. Server models carry no Serde annotations (the library converts through compile-time
  introspection, never Jackson). Client models are serdeable.
- There is no generated type dictionary. The library derives union and interface members from sealed types and
  `@GraphQLType`.
- Output targets Java 17: no newer language feature or API in generated code.
- Generated sources go into the application's own source set by default.
- Client mode emits Java client models only in 1.0.

Out of scope for version 1 (each has its own issue): compiled wiring (#111), the post-compile verification task, schema
export, per-type model style, Kotlin and Groovy emitters for client models, generated fetchers for hand-written
resolvers, wildcard generics.

## Pipeline

```
schema files --> loader --> IR --> rules --> emitter --> files
 (*.graphqls)   (graphql-java        (naming, mapping,   (one per language,
                 parser only)         diagnostics)        behind one interface)
```

- The loader reads files in a defined order, parses them with graphql-java's parser and merges type extensions. No other
  stage touches graphql-java.
- The rules lower the merged definitions plus the `Options` into the IR: they apply names, type mappings, model style and
  contract grouping, and they report diagnostics. After lowering, every decision is in the IR.
- An emitter takes the IR and returns files. It makes no naming or mapping decision; it only turns IR nodes into the
  syntax of one language. This is what lets the golden harness (#192) run any emitter against one IR snapshot.

## The IR

The IR is a tree of immutable Java 17 records, language neutral, and a pure function of the schema files and the
`Options`. It is also what the IR text snapshots (#16) print. Rules and emitters see nothing but the IR.

```java
record Ir(List<EnumType> enums, List<InputType> inputs, List<ModelType> models,
          List<AbstractType> abstractTypes, List<ScalarType> scalars,
          List<ResolverContract> contracts,
          List<OperationDocument> operations, List<FragmentDefinition> fragments) {}

record ModelType(Names names, Docs docs, List<Field> fields,
                 List<String> memberOf, List<String> implemented, Origin origin) {}
record InputType(Names names, Docs docs, List<InputField> fields, Origin origin) {}
record EnumType(Names names, Docs docs, List<EnumValue> values, Origin origin) {}
record AbstractType(Kind kind, Names names, Docs docs, List<Field> fields,
                    List<String> members, boolean sealed, Origin origin) {}
record ScalarType(Names names, Docs docs, TypeMapping mapping, Origin origin) {}
record ResolverContract(Kind kind, Names names, String marker, List<ContractMethod> methods,
                        Origin origin) {}
record ContractMethod(String name, Coordinate coordinate, TypeRef returns,
                      List<Argument> arguments, Wrapper wrapper, Docs docs) {}
```

`operations` and `fragments` are empty in server mode; the client section below describes them.

Shared pieces:

- `Names(String sdlName, String packageName, String simpleName)`: the SDL name and the generated name side by side. The
  emitter writes `@GraphQLType(name = ...)` when they differ, and `@GraphQLSchemaCoordinate` only when a method name
  differs from the field name.
- `TypeRef` is `Builtin`, `Mapped` (an external class from `typeMappings`), `Reference` (to another IR node by SDL name)
  or `ListOf`, and every node carries `nullable`. The IR keeps the GraphQL nullability exactly. Primitive or boxed
  representation is an emitter rule over `nullable`: a non-null `Int`, `Float` or `Boolean` in a direct value position (a field, component,
  argument or return type) is a primitive, and everything else, including every list element and every generic type
  argument, is a reference type. The golden snapshots pin it. One exception is lowered into the IR as `Field.boxed`: see
  "Fields that implement an interface field".
- `Docs(String description, Deprecation deprecation)`: the SDL description (or the comment, under
  `commentsAsDescriptions`) and the deprecation reason. Emitters turn them into Javadoc or KDoc and `@Deprecated`.
- `Origin(String path, int line, int column)`: the schema file relative to the schema root, with `/` separators. It is
  used for diagnostics and IR snapshots only, and never written into generated code, so output does not depend on where a
  checkout lives.
- `Wrapper` is the return shape of a contract method: plain by default; `suspend`, `Flow` and futures only when an option
  asks for them.
- Applied directives are kept as name plus string-valued arguments, because the selector options name directives.
- Every list in the IR is in schema order (see Determinism). No node holds a graphql-java object.

Diagnostics are produced while lowering, as `Diagnostic(severity, code, message, optionName, origin)`: an unmapped custom
scalar, a keyword or `Object`-method name used as a field or enum value, a name that two types resolve to, an invalid
option combination. The message names the option or the schema element and gives its location.

## Fields that implement an interface field

A generated interface and its implementations must compile, so two cases of a field that implements an interface field are
decided while lowering, and the emitter only reads the result. They apply to every object type and every interface that
implements an interface, through the interfaces that it implements, directly or by a chain.

- **A narrowed list keeps the element type of the interface.** Java generics are invariant: a record component
  `List<Folder> children` does not implement `List<Node> children()`, and wildcard generics are out of version 1. A single value is
  covariant, so `Folder parent` implements `Node parent()` and is left alone. For a list (at any depth) whose element type is another
  type than the one the interface declares, the field takes the interface's element type with the nullability of its own schema type
  at each level, and lowering reports the warning `narrowed-list-field`. The generated model is then wider than the schema says (a
  `List<Node>`); the runtime still returns the narrower objects, and the generated code never reads the narrower type. The
  alternative of an error would reject a valid schema with no way to fix it in the schema, and the wildcard `List<? extends Node>`
  needs a rule for every consumer of the type, which is why it stays out.
- **A strengthened scalar keeps the boxed representation.** A non-null `Int`, `Float` or `Boolean` is a primitive in a direct value
  position, and a primitive return cannot override the boxed return of the interface. When any implemented interface declares the
  field as a nullable scalar, or as one that is itself boxed for this reason, the field has `boxed` set and the emitter writes the
  boxed class (`Integer`), with the non-null nullability of the schema kept in the IR.
- **A conflict is an error.** When two implemented interfaces declare one field with different list element types, or one as a
  primitive and the other as a nullable scalar, no single type implements both, and lowering reports `interface-field-conflict` at the
  implementing field.

## Options model

One immutable `Options` record is the single source of truth. The Gradle extension, the Maven mojo parameters and the
builder of the library all map onto its components by name; none of them defines an option the record does not have.

Every component has a primitive or string-valued form, so options cross Gradle worker isolation and Maven XML binding
without a custom type:

| Component type | String form | Gradle | Maven |
| --- | --- | --- | --- |
| `String` | itself; an absent key is the default, a present empty value is the empty string (for a class-name option, "none"; for a package option, "derive from `packageName`") | `Property<String>` | element text |
| `boolean`, `int` | `true` and `false`, decimal | `Property<Boolean>`, `Property<Integer>` | element text |
| enum | the constant name | `Property<String>` | element text |
| `List<String>` | `name.size`, then `name.0`, `name.1`, ... (an empty list is `name.size=0`) | `ListProperty<String>` | child elements |
| `Map<String,String>` | `name.<key>` | `MapProperty<String,String>` | child elements named by key |

Why strings: a worker started with `classLoaderIsolation` loads the generator in its own class loader, so an enum or
record of the generator cannot be handed over from the plugin's class loader. The task passes `Options.toMap()`, a
`Map<String,String>`, and the worker calls `Options.fromMap`. This also makes the string form the input of the
up-to-date and build-cache keys.

Rules:

- `fromMap(toMap(options))` equals `options` for every valid `Options` (a unit test in #193 enforces it). `toMap()` lists
  only values that differ from the default, in key order, so the form is canonical.
- `fromMap` rejects an unknown key, an invalid enum value (the message lists the valid ones) and an unparsable number, each
  as a diagnostic that names the option.
- `Options.validate()` returns diagnostics for invalid combinations. It never throws for them. Example: `kotlinSuspend`
  with a language other than Kotlin.
- Option names use GraphQL vocabulary and do not reuse the compile-time processor's option names.
- Schema files, the output directory and the language-independent builder wiring are not options; they are arguments of
  `GraphQLCodeGenerator.builder()`.

The initial set (final names, javadoc and defaults are written in #193; a later issue adds its options to the same record
under the same rules):

| Group | Option | Form | Default |
| --- | --- | --- | --- |
| Output | `language` | enum `JAVA`, `KOTLIN`, `GROOVY` | `JAVA` |
| Output | `mode` | enum `SERVER`, `CLIENT` | `SERVER` |
| Output | `packageName` | string | required |
| Output | `modelPackage`, `resolverPackage`, `namesPackage` | string | `<packageName>.model`, `.resolver`, `.names` |
| Output | `generatedAnnotation` | class name, empty for none | `javax.annotation.processing.Generated`, no date |
| Models | `modelStyle` | enum `RECORD`, `BEAN` | `RECORD` |
| Models | `interfaceTypes` | list of SDL object type names | empty |
| Models | `typeNamePrefix`, `typeNameSuffix` | string | empty |
| Models | `nullableAnnotation` | class name, empty for none | `io.micronaut.core.annotation.Nullable`; `org.jspecify.annotations.Nullable` in client mode |
| Contracts | `rootContracts` | boolean | `true` |
| Contracts | `contractNameTemplate` | string | `{Field}{Operation}Resolver` |
| Contracts | `kotlinSuspend` | boolean | `false` |
| Contracts | `subscriptionReturnType` | class name | `org.reactivestreams.Publisher` |
| Mapping | `typeMappings` | map from SDL name or `Type.field` to a class | empty, plus built-in presets |
| Mapping | `typeMappingsFromDependencies` | boolean | `true` |
| Names | `nameConstants` | enum `NONE`, `TYPES`, `FIELDS` | `FIELDS` |
| Schema | `includes`, `excludes` | list of globs | `**/*.graphqls`, `**/*.graphql`; none |
| Schema | `commentsAsDescriptions` | boolean | `false` (must match the runtime setting) |

An unmapped custom scalar is always a diagnostic, never an option, because the third-party generators' silent fallback
to `String` is the failure this generator exists to avoid.

## Emitter strategy

### The question

Two shapes were possible:

1. Language-specific emitters behind one emitter interface: the JavaPoet fork that ships with micronaut-sourcegen for Java
   and Groovy, and a Kotlin writer for Kotlin.
2. The language-neutral sourcegen model (`ObjectDef`, `RecordDef`, `InterfaceDef`, ...) with sourcegen's own Java, Groovy
   and Kotlin writers.

### The spike

The spike generated the same artifacts through each candidate and measured them. It is throwaway and is not part of this
repository; the method is repeated here so a later reader can redo it.

- Artifacts per schema: enums, one record (or data class) per object and input type with nullability, Javadoc and
  `@Introspected`, a sealed union with its members, one resolver contract interface per root field with arguments, and
  name constants. Java and Groovy use records and `sealed ... permits`; Kotlin uses data classes and `sealed interface`.
- Candidates, per language: (a) the sourcegen model with its writer; (b) the fork directly for Java and Groovy,
  KotlinPoet directly for Kotlin; (c) plain string building, as a floor for what the emitter work itself costs.
- Schemas: the `test-suite-java` schema (17 types, 48 fields, 33 files), and a synthetic schema of 1,000 types and 20,000
  fields: 1 `Query` with 620 fields, 699 object types of 20 fields, 150 input types of 36 fields, 100 enums and 50 unions
  (536 KB of SDL, 1,620 generated files, about 2.4 million characters).
- "Emit" is the IR to in-memory file contents. "Parse" is graphql-java's parser plus the conversion to the IR. "Write" is
  writing the files to a fresh temporary directory. Cold is the first run in a fresh JVM, taken from 5 to 7 fresh JVMs;
  warm is the median of iterations 21 to 30 of a 30-iteration run, taken from 4 JVMs. All candidates ran back to back, in
  alternating order, with the same flags (`-Xmx512m`).
- Versions: micronaut-sourcegen 1.8.5 (the version the Micronaut platform 4.10.25 manages, the last 1.x release),
  KotlinPoet 2.0.0 (also platform-managed), micronaut-core 4.10.37 on the generator classpath, graphql-java 22.4. Build
  JVM: Temurin 21.0.13 for the main series, and Temurin 17.0.21 and 25.0.5 for the cross-check. Machine: Apple M1 Max,
  10 cores, 64 GB, macOS. Other builds ran on the machine during the runs: the 1-minute load average was between 3.4 and
  9.8. The spread inside every series stayed within about 10 percent, so the ranking never changed with the load.
- The generated sources were compiled with javac 21 and 17, groovyc 4.0.33 and kotlinc 2.0.10, against stand-ins for the
  marker interfaces and the real `Introspected` and `Nullable` annotations of micronaut-core 4.10.37.

### Results on the synthetic schema (1,000 types, 20,000 fields)

Cold emit, in milliseconds, minimum to maximum over 7 fresh JVMs (Temurin 21):

| Language | Plain text (floor) | (b) direct poet | (a) sourcegen model |
| --- | --- | --- | --- |
| Java | 34 to 41 | 358 to 400 | 420 to 457 |
| Groovy | 33 to 37 | 361 to 394 | 421 to 471 |
| Kotlin | 33 to 39 | 789 to 832 | 955 to 990 |

Parse was 242 to 271 ms cold in every series, and writing the 1,620 files was 177 to 206 ms cold. The whole process
(JVM start, parse, emit, write, exit) took a median of 1,002 ms for Java (b), 1,081 ms for Java (a), 1,440 ms for Kotlin
(b) and 1,610 ms for Kotlin (a).

Warm emit, in milliseconds, median of iterations 21 to 30, range over 4 JVMs (Temurin 21):

| Language | Plain text (floor) | (b) direct poet | (a) sourcegen model |
| --- | --- | --- | --- |
| Java | 5 to 6 | 125 to 130 | 145 to 147 |
| Groovy | 5 | 128 to 133 | 147 to 150 |
| Kotlin | 5 | 439 to 467 | 479 to 507 |

The second run in the same JVM took 158 to 177 ms for Java (b) and 446 to 477 ms for Kotlin (b). Cross-check on other build
JVMs, cold emit and warm emit for (b) Java: Temurin 17 405 to 438 ms and 120 to 130 ms, Temurin 25 338 to 379 ms and 143
to 148 ms. For (b) Kotlin: Temurin 17 800 to 822 ms and 460 to 478 ms, Temurin 25 745 to 801 ms and 428 to 439 ms.

On the test-suite schema (17 types), cold emit was 26 to 30 ms for Java (b), 42 to 43 ms for Java (a), 60 to 66 ms for
Kotlin (b) and 108 to 118 ms for Kotlin (a).

The output compiles and costs far more to compile than to generate: javac 21 took 2.4 s for the generated Java of the
synthetic schema (2,470 classes), groovyc 6.1 s and kotlinc 16.5 s, each in a fresh JVM.

Each candidate's output was byte-identical in every run: one digest per candidate across all fresh JVMs, all in-JVM
iterations and the three build JVMs. No output contained a `\r`.

The classpath the generator needs, summed from the resolved jars: the fork alone is 137 KB and runs with only the JDK (the
spike ran it on that jar alone); the sourcegen Java writer with its Micronaut and JavaParser closure is 5.1 MB;
KotlinPoet with the Kotlin standard library and reflection is 5.2 MB; sourcegen's Kotlin writer adds the model and the
Micronaut closure on top of that.

### Gaps found

| Gap | Fork or KotlinPoet used directly | Sourcegen model and writers |
| --- | --- | --- |
| `permits` for a sealed union | Supported (`addPermittedSubclass`); javac and groovyc compile the output | The model has `SEALED` but no `permits`. The Java and Groovy output is `sealed interface E` with no `permits`, and javac fails on it ("sealed class must have subclasses", "not listed in its permits clause"), as does groovyc. Kotlin needs no `permits` |
| Type-use nullability on record components | `TypeName.annotated` gives `@Nullable String f` and `List<@Nullable T>`; javac and groovyc accept a type-use only annotation on record components, and reflection sees it on the component's annotated type. Kotlin uses `T?` | `makeNullable()` emits no annotation in Java and Groovy output (the nullability is lost); only an explicit `annotated(...)` writes it |
| Javadoc | Supported on types. A record component has no Javadoc in the language, so component descriptions go into the type's Javadoc as `@param` lines | Same |
| Javadoc content | Neither library escapes it: a description containing `*/` ends the comment and breaks the build, and a `\u` sequence is read inside comments. The generator must escape descriptions itself (`*/`, `\u`, `<`, `&`, `{@`, `@`), once, for all emitters | Same |
| Groovy `record`, `sealed ... permits`, `non-sealed` | groovyc 4.0.33 compiles all three natively on JDK 17 and 21, with `@CompileStatic` and a type-use annotation on components | Sourcegen's Groovy writer is its Java writer plus `@CompileStatic`, so it has the `permits` gap |
| Kotlin constants and annotations | KotlinPoet writes `object` with `const val`, and `@Generated("...")` | The writer throws on the `STATIC` modifier, writes a string constant without quotes, writes a nested `class` with `var` instead of an `object` with `const val`, and writes `@Generated(value = "...")`, which kotlinc rejects for an array-valued member. It writes `public final val` and `public constructor` noise |
| Nullable data class defaults | A rule of ours: a nullable input component gets `= null` | Not expressible as such |
| Cosmetics | Imports, indent and line wrapping controlled | `import java.lang.String;` and fixed two-space indent; no effect on correctness |

Through the sourcegen model, the Java and Groovy output of the test-suite schema failed to compile at the sealed union and
at each of its two members, and the Kotlin output failed on every file with `@Generated` and on the name constants. With
the fork and KotlinPoet used directly, the output of all three languages compiled: the test-suite schema on JDK 17 and 21,
and the synthetic schema on JDK 21.

### Decision

Confirmed, and made concrete: language-specific emitters behind one emitter interface.

- Java and Groovy: the JavaPoet fork that sourcegen ships, used directly (`io.micronaut.sourcegen.javapoet`). Groovy is the
  Java emitter plus `@CompileStatic`; the shared code is one class parameterized by that difference.
- Kotlin: KotlinPoet, used directly. Sourcegen's Kotlin writer is not used: it is 10 to 20 percent slower in the
  spike (cold and warm), it produced non-compiling output for constants and `@Generated`, and it needs the model and the Micronaut
  closure.
- The sourcegen model is adopted only if upstream gains `permits` (#112) and the writer gaps above close, and only if a
  measurement shows a gain. Nothing blocks on it. Adopting it later replaces an emitter's internals, not the IR and not the
  interface.
- The sourcegen line is 1.x, which is the Micronaut 4 line. 1.8.5 is the last 1.x release; its class files target Java 17.
  The 2.x line targets Java 25 and Micronaut 5, so it does not fit the Micronaut 4 practice of this library.
- Dependencies: `io.micronaut.sourcegen:micronaut-sourcegen-generator-java`, declared non-transitive (the fork runs with
  only the JDK), and `com.squareup:kotlinpoet`. The Micronaut platform already manages both (1.8.5 and 2.0.0), so no
  version is declared and no catalog entry is needed. They are on the generator classpath of the plugins only, never on an
  application's classpath. The fork is an implementation detail of that artifact, not a documented API, so the version
  stays pinned by the platform and the golden files (#192) catch any change in output.
- The emitter interface (#191) takes the IR and the options that concern emission and returns `List<GeneratedFile>`, a path
  with `/` separators and the text. It names no Java type. It is extracted from the working Java emitter, as planned.

### Targets

The working targets were at most 1 s cold, 300 ms warm for 1,000 types and 20,000 fields, and 0 ms when nothing changed.
Measured, with the cold figure now stated end to end (parse, emit and write, without JVM start):

| Target | Java (b) | Groovy (b) | Kotlin (b) | Verdict |
| --- | --- | --- | --- | --- |
| Cold, end to end, at most 1 s | 0.85 to 0.93 s | about 0.85 to 0.95 s (estimate, see below) | 1.2 s | Confirmed for Java and Groovy, revised for Kotlin to 1.5 s |
| Warm emit, at most 300 ms | 125 to 130 ms | 128 to 133 ms | 320 to 360 ms with per-type name classes, 440 to 470 ms with one name file | Confirmed for Java and Groovy, revised for Kotlin to 400 ms |
| Unchanged | 0 ms under Gradle (task skipped) | 0 ms under Gradle | 0 ms under Gradle | Confirmed for Gradle; under Maven the fingerprint check costs 14 ms of hashing in a fresh JVM, see below |

The cold figures use the default layout of one name class per type: for Java, parse 244 to 256 ms, emit 344 to 390 ms and
write 262 to 285 ms (2,469 files); for Kotlin, 247 to 255, 685 to 696 and 258 to 280 ms. With one name file (1,620 files)
Java is 0.78 to 0.86 s and Kotlin 1.2 to 1.3 s. Groovy emits through the same code as Java and was not measured with
per-type name classes, so its figure is an estimate from Java's (the one-file Groovy emit was within 5 percent of Java's).

KotlinPoet is about 2.5 times slower than the JavaPoet fork on the same artifacts, and the plain-text floor shows how much
of any poet is overhead. If Kotlin generation ever shows up in a build profile, the Kotlin emitter can be rewritten as
templates behind the same interface: the golden files (#192) pin its output, so the rewrite is safe. That is a recorded
option, not a v1 decision.

Unchanged inputs cost nothing because the Gradle task is cacheable and skipped by its up-to-date check, so the generator does
not run. For Maven, which has no up-to-date engine, the mojo compares a fingerprint of the schema files, the canonical
option string and the generator version before it generates. Hashing the 536 KB synthetic schema plus options took 14 ms in a fresh JVM and 0.5 ms
warm. The generator also writes a file only when its content differs, so file times and downstream compile inputs stay
untouched when a regeneration produces the same bytes.

The generator scales with schema size: the cold figures above are dominated by class loading and JIT warm-up, so a schema ten
times smaller costs a fraction of them (the 17-type schema emits in 26 to 66 ms).

## Determinism

The output is a pure function of the schema files, the options and the generator version. Two runs produce byte-identical
files, on any file system, locale, build JVM and operating system.

- Schema order. The loader sorts the schema files by their relative path (`/` separators, UTF-8 byte order) after
  applying `includes`, `excludes` and de-duplication, so a different directory listing order changes nothing. Definitions
  keep their order within a file. An extension is merged after its base definition, in file order. Members, fields,
  arguments, enum values and union members keep schema order; nothing is sorted alphabetically. The IR uses ordered
  collections only, and output files are written in sorted path order.
- Text. Files are UTF-8 with `\n` line endings on every platform, ending in one newline. The fork and KotlinPoet contain no call that
  reads the platform line separator (checked in their bytecode). Case conversion uses
  `Locale.ROOT`, so a Turkish default locale changes nothing.
- No timestamps and no environment. `@Generated` carries no date. The generator version may appear in `@Generated`; no
  absolute path, user name, host name or clock value is written anywhere.
- Collisions resolve by rule. Two SDL names that map to one generated name are a diagnostic, not "first wins".
- Verified in the spike: one digest per candidate across 30 in-JVM iterations, across 5 to 7 fresh JVMs, and across Temurin
  17, 21 and 25. #192 turns this into a test with shuffled input order.

## Placement of name constants

Name constants are generated as classes, not resources.

- A class constant is a compile-time constant. It is usable in annotation arguments (`@GraphQLType(name = ...)`,
  `@GraphQLSchemaCoordinate(...)`), is inlined by the compiler, costs nothing at startup and fails the build when a name
  disappears. A resource would add a startup read, give no compile-time check and be unusable in annotations. Resources
  stay for data that tools read at build time, such as the type-mapping properties of #210.
- There is no type dictionary, in either form (see the settled points). A facts index is not emitted either: the
  compile-time checks see the generated contracts as ordinary sources in the application's own source set, and no consumer
  needs a second copy. The guide says so when the generator ships.
- Layout: one class per object, interface and input type (the types that have fields), `{Type}Names`, with a `TYPE` constant and one
  constant per field, in the names package (a Kotlin `object` with `const val`). The names of enums, unions and
  scalars, and of every other type under `nameConstants = TYPES`, are constants of one class `GraphQLTypeNames`. `NONE`
  emits nothing. The spike emitted the per-type classes and did not emit `GraphQLTypeNames`.

Why a class per type and not one class for the schema:

- A single class has a hard limit. Compiling one class of 21,000 unique string constants worked and 22,000 failed with
  "too many constants" (the class-file constant pool, about 3 entries per constant). A schema of 1,000 types and 20,000
  fields sits at that edge; a larger one breaks the build.
- A class that lists every type is recompiled whenever any type changes. A class per type keeps a change local.
- It also generates faster in Kotlin: 685 to 696 ms cold and 318 to 361 ms warm with a class per type, against 782 to 830
  and 447 to 474 with one nested file. In Java it makes no difference to emit time.

The cost, measured on the synthetic schema in Java (850 per-type classes instead of one nested file, so 849 more files), writing took 262 to 285 ms instead of 184 to 206 ms,
and compiling only the name classes took 0.98 s instead of 0.84 s. Field-level constants are about half of the generated
bytes (1.2 MB of 2.5 MB) and about 40 percent of the javac time of the whole output (about 1 s of 2.4 s). That is why
`nameConstants` exists: the default `FIELDS` suits typical schemas, and a very large schema can choose `TYPES` or `NONE`
and drop almost all of that cost.

## Client mode

Client mode uses the same loader, the same IR core and the same emitter interface. It adds nodes and rules; it adds no
second pipeline, and the work is shared with the operation IR (#667) and the Java emitter for client models (#671).

Extra IR nodes, filled only when `mode = CLIENT`:

```java
record OperationDocument(Names names, OperationKind kind, List<Variable> variables,
                         SelectionSet selection, String documentText, String documentHash,
                         List<String> fragments, Origin origin) {}
record FragmentDefinition(Names names, String typeCondition, SelectionSet selection, Origin origin) {}
record SelectionSet(List<SelectedField> fields, List<InlineFragment> inlineFragments,
                    List<String> fragmentSpreads) {}
record SelectedField(String alias, String fieldName, List<Argument> arguments,
                     TypeRef type, SelectionSet selection) {}
record Variable(String name, TypeRef type, String defaultValue) {}
```

- The result models of an operation are derived from its selection set by the rules: one record per selection set, named
  by the path from the operation, a fragment interface for each named fragment that the records spreading it implement,
  and a sealed interface for an abstract selection with one record per type condition and an `Other` record. The emitter
  sees them as ordinary `ModelType` and `AbstractType` nodes plus the operation request class.
- Input objects, enums and custom scalars that an operation reaches reuse `InputType`, `EnumType` and `ScalarType` and the
  same `typeMappings`.
- `documentText` and `documentHash` are the assembled document that the compile-time processor also validates, so the two
  agree by construction (#667 asserts equality).
- Variables, selection sets, fragments and abstract types need no new emitter capability: they are records, interfaces and
  sealed interfaces, which the Java emitter already writes. Nullability uses the JSpecify annotation by default in client
  mode (a type-use only annotation works on record components), and the client models are serdeable.
- Client models are emitted as Java only in 1.0. Kotlin callers read the JSpecify nullability and Groovy compiles jointly.
  The emitter interface leaves room for Kotlin and Groovy client emitters later without an IR change.

## Effects on other work

- #193: the options table and string-form rules above are its starting point; the module depends on graphql-java (parser
  only), the sourcegen fork (non-transitive) and KotlinPoet, both managed by the platform; add `nameConstants`,
  `nullableAnnotation` and `generatedAnnotation` to the option list.
- #16: the IR sketch and the lowering split above; escape descriptions once in the shared documentation step; the spike's
  parse figure (242 to 271 ms cold for 1,000 types and 20,000 fields) is the baseline to compare against.
- #192: the harness compiles Java with javac, Kotlin with kotlinc and Groovy with groovyc, and checks identical bytes across
  runs; keep the large-schema compile out of the unit suites (kotlinc took 16.5 s on it).
- #191: the interface is `emit(Ir, ...)` returning `List<GeneratedFile>`, with no Java types; the Java emitter is the fork
  used directly.
- #187: KotlinPoet directly; the writer gaps above are why sourcegen's Kotlin writer is not used. `const val` objects are
  straightforward in KotlinPoet.
- #189: Java-style emitter plus `@CompileStatic`; native `record`, `sealed ... permits` and `non-sealed` compile on groovyc
  4.0.33. Whether Micronaut's Groovy AST transforms introspect Groovy records stays that issue's test.
- #112: the upstream contribution needs `permits` on the model and the Java writer, plus tests for Java and Kotlin; the
  writer gaps in the table are optional extras. Target line 1.x.
- #128: no concrete consumer of a facts index exists, so it is not emitted; the type dictionary stays dropped.

## What this does not decide

- The final option names, Javadoc and defaults: #193.
- The scalar presets and built-in mappings: #210.
- The contract grouping and naming templates beyond the default: #198 and the issues that follow it.
- Whether Micronaut's introspection builds beans for Groovy records: #189.
- The annotation-processor path of the compile-time checks over generated sources: covered end to end by #213.
