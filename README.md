# Micronaut GraphQL Tools

[![Build Status](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml/badge.svg)](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml)

Schema-first approach for building GraphQL applications with Micronaut.

This is not an official Micronaut module yet: it is developed here and is intended for later contribution to the Micronaut Foundation. Milestone previews (`1.0.0-M<n>`) are published to the GitHub Packages Maven registry of this repository (see [Snapshots and Releases](#snapshots-and-releases)); nothing is published to Maven Central.

## Documentation

The guide sources are in `src/main/docs/guide`; build them with `./gradlew docs` (see [CONTRIBUTING.md](CONTRIBUTING.md)). No documentation site is published yet.

## Modules

The repository is a Gradle build with one project per folder. The table lists each project, its Gradle path and what it is
for. Every folder has a README with its purpose, its responsibility, what is public and what is internal, and how to build
and test it.

| Module | Gradle project | Purpose |
| --- | --- | --- |
| [`graphql-tools`](graphql-tools/README.md) | `:micronaut-graphql-tools` | The runtime library that applications depend on. |
| [`graphql-tools-binding`](graphql-tools-binding/README.md) | `:micronaut-graphql-tools-binding` | The binding rules used by the runtime library. |
| [`graphql-tools-scalars`](graphql-tools-scalars/README.md) | `:micronaut-graphql-tools-scalars` | The standard scalars: `java.time`, JSON and extended scalars, as beans. |
| [`graphql-tools-micrometer`](graphql-tools-micrometer/README.md) | `:micronaut-graphql-tools-micrometer` | Request metrics (Micrometer), optional. |
| [`graphql-tools-opentelemetry`](graphql-tools-opentelemetry/README.md) | `:micronaut-graphql-tools-opentelemetry` | Request and field spans (OpenTelemetry), optional. |
| [`graphql-tools-upload`](graphql-tools-upload/README.md) | `:micronaut-graphql-tools-upload` | File upload: the `Upload` scalar and the multipart request route, optional. |
| [`graphql-tools-security`](graphql-tools-security/README.md) | `:micronaut-graphql-tools-security` | Micronaut Security: the principal in resolvers, `@Secured` on resolver methods and the root fields hook, optional. |
| [`graphql-tools-testing`](graphql-tools-testing/README.md) | `:micronaut-graphql-tools-testing` | The in-process tester for application tests: run a document against the `GraphQL` bean and assert on the response, test scope. |
| [`graphql-tools-processor`](graphql-tools-processor/README.md) | `:micronaut-graphql-tools-processor` | The annotation processor that runs the compile-time checks. |
| [`graphql-tools-bom`](graphql-tools-bom/README.md) | `:micronaut-graphql-tools-bom` | The bill of materials for the published artifacts. |
| [`docs-examples-java`](docs-examples-java/README.md) | `:micronaut-docs-examples-java` | The guide examples in Java, with a test that runs the quickstart. |
| [`docs-examples-kotlin`](docs-examples-kotlin/README.md) | `:micronaut-docs-examples-kotlin` | The guide examples in Kotlin, with a test that runs the quickstart. |
| [`docs-examples-groovy`](docs-examples-groovy/README.md) | `:micronaut-docs-examples-groovy` | The guide examples in Groovy, with a test that runs the quickstart. |
| [`test-suite-tck`](test-suite-tck/README.md) | `:test-suite-tck` | The execution-behaviour scenarios that the example suites run. |
| [`test-suite-benchmarks`](test-suite-benchmarks/README.md) | `:test-suite-benchmarks` | JMH benchmarks and the allocation gate. |
| [`test-suite-java`](test-suite-java/README.md) | `:test-suite-java` | The example application in Java, with its tests. |
| [`test-suite-serde`](test-suite-serde/README.md) | `:test-suite-serde` | The null-field checks on Micronaut Serialization, with their native test. |
| [`test-suite-kotlin`](test-suite-kotlin/README.md) | `:test-suite-kotlin` | The example application in Kotlin, with its tests. |
| [`test-suite-groovy`](test-suite-groovy/README.md) | `:test-suite-groovy` | The example application in Groovy, with its tests and a native smoke test. |
| [`test-suite-build`](test-suite-build/README.md) | `:test-suite-build` | Sample projects that apply the compile-time checks the way a consumer's build does, built by Gradle TestKit. |

The root project (`graphql-tools-parent`, `build.gradle.kts`) aggregates the modules and the shared build settings. The
guide is in `src/main/docs/guide`, and the build convention plugin is in `buildSrc`.

## Quick start

1. Add the repository and the dependency in build.gradle or pom.xml. Milestone previews come from the GitHub Packages registry of this repository, which needs a token even for downloads ([Consuming milestone previews](#consuming-milestone-previews) has the full setup; no version is on Maven Central):

Gradle:

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/donbeave/micronaut-graphql-tools")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
        content { includeGroup("io.micronaut.graphql.tools") }
    }
}

dependencies {
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-M4")
}
```

The library does not bring a JSON codec (BC-26). Add the one your application uses, for example `micronaut-jackson-databind` or `micronaut-serde-jackson`.

Maven (the repository goes in the POM, the credentials in `~/.m2/settings.xml`, see [Consuming milestone previews](#consuming-milestone-previews)):

```xml
<repositories>
    <repository>
        <id>github-graphql-tools</id>
        <url>https://maven.pkg.github.com/donbeave/micronaut-graphql-tools</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.micronaut.graphql.tools</groupId>
        <artifactId>micronaut-graphql-tools</artifactId>
        <version>1.0.0-M4</version>
    </dependency>
</dependencies>
```

2. Define GraphQL Schema

Create GraphQL schema files, `*.graphqls`, to define types, queries, and mutations, in `src/main/resources/graphql`. The library finds them at startup, see [Schema discovery](#schema-discovery); a `schema { }` block is optional.

3. Configuring GraphQL Tools

Nothing to configure for the default location. To load the schema yourself, define a `TypeDefinitionRegistry` bean; it replaces the discovered schema. The example reads `schema.graphqls` from the root of the classpath; a file in the default folder is `classpath:graphql/schema.graphqls`.

```java
@Bean
@Singleton
public TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) {
    InputStream inputStream = resourceResolver.getResourceAsStream("classpath:schema.graphqls").get();
    return new TypeDefinitionRegistry()
            .merge(new SchemaParser().parse(new BufferedReader(new InputStreamReader(inputStream))));
}
```

4. Using Annotations for GraphQL Components

- `@GraphQLRootResolver` Defines a root-level resolver, representing a GraphQL entry point, such as a query or mutation.
- `@GraphQLType` Maps a Java class to a GraphQL type.
- `@GraphQLTypeResolver` Specifies a resolver for a specific GraphQL type field, enabling mapping of nested or complex fields within types.
- `@GraphQLInput` Defines a GraphQL input type, representing structured input data for mutations or queries.
- `@GraphQLModels(classes, packages)` On any class of the module that compiles the resolvers: generates the bean introspections of model classes that carry no annotation (generated classes, classes of another module or library), see the guide page Model Classes.
- `@GraphQLField` Marks a method of a `@GraphQLType` class as a field resolver, so that methods with arguments (or a `DataFetchingEnvironment` parameter) can back a GraphQL field. `@GraphQLField("name")` on a method or a property binds the field of that name, see [Binding a field to a differently named member](src/main/docs/guide/binding.adoc).
- `@GraphQLIgnore` Marks a method, a property or an enum constant that binds no field, so that strict mode does not report it, see [Escape hatches](#escape-hatches).
- `@GraphQLSchemaCoordinate("Type.field")` Names the field a resolver method binds; meant for resolver contracts (interfaces), see [Resolver contracts](src/main/docs/guide/resolverContracts.adoc).

These are all the annotations the library provides (package `io.micronaut.graphql.tools.annotation`). GraphQL enums map to Java enums (with `@Introspected`, see [Enums](src/main/docs/guide/typeSystem.adoc)), and unions and interfaces to Java interfaces, without an annotation of their own; the concrete classes of a union are registered as shown in [Custom types mapping](src/main/docs/guide/typeSystem.adoc).

## Schema discovery

Without a `TypeDefinitionRegistry` bean the library reads the schema files of `micronaut.graphql.tools.schema.locations` at
startup, merges them, and binds the result. The default is `classpath:graphql/**/*.graphqls`: every `.graphqls` file under
`graphql/` of the classpath, in sorted order, each parsed with its own name, so that errors and the
[binding manifest](#binding-manifest) name the file. No factory is needed.

| Location | Reads |
|---|---|
| `classpath:graphql/**/*.graphqls` | the first classpath entry that has a `graphql/` directory (your application) |
| `classpath*:graphql/**/*.graphqls` | every classpath entry that has one, dependency jars included |
| `file:/etc/app/schema/*.graphqls` | the file system |

A location is a scheme and an Ant-style path (`*`, `**`, `?`). A path without wildcards names one file and fails when it is
missing. A wildcard needs a directory before it. Several locations are read in order and merged.

- **Implicit roots.** A schema without a `schema { }` block, and with a `Query` type, uses the object types `Query`, `Mutation` and `Subscription`
  that it defines as the root operation types.
- **Operation files.** The default reads `*.graphqls` only. A file that holds only executable definitions (queries,
  mutations, fragments) or nothing is skipped and logged at INFO, so client operation files in the same directory do not
  break the startup. A file that mixes operations and type definitions fails.
- **Override.** Any `TypeDefinitionRegistry` bean of the application is used instead and nothing is read. A
  `GraphQLTypeDefinitionRegistryCustomizer` still runs on top of the schema, with either source.
- **Failures.** A file that does not parse or conflicts with another, a location that names a missing file, and locations
  that match no file at all fail the startup: code `schema-invalid` for a file that does not parse or conflicts, `schema-not-found` for the rest. The message names the file or the locations.
- **Compile time.** The processor reads the same `*.graphqls` files with the same code from its schema directory
  (`micronaut.graphql.tools.processor.schema.dirs`, see [Compile-time checks](#compile-time-checks)): several directories may be listed
  with the platform path separator. It cannot read classpath locations, so schema that comes from a dependency jar is
  not checked at compile time; point the directory option at a copy of it, or accept that the startup checks it.

### Native image

A native image cannot list a classpath directory, so a wildcard `classpath:` or `classpath*:` location reads the schema
index of the module instead: the resource `META-INF/micronaut-graphql-tools/schema-index.txt`, one classpath path per line
(a jar that provides schema files ships its own, and the native image resource metadata of those files: the index only lists them). The index is read in a native image only; the JVM lists the directories. A `classpath:` location reads the first index that has a matching file, because the index does not record which classpath entries have the directory: an application whose `graphql/` directory holds only operation files does not shadow the schema of a dependency in a native image, as it does on the JVM.
The index is not written by the processor. Generate it in the build, from the files that are packaged, and keep the schema
files themselves in the native image resources (the Micronaut Gradle plugin includes `src/main/resources`):

```kotlin
val schemaIndex = tasks.register("graphqlSchemaIndex") {
    val schemaFiles = fileTree("src/main/resources") { include("graphql/**/*.graphqls") }
    val output = layout.buildDirectory.dir("generated/graphql-index")
    inputs.files(schemaFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
    doLast {
        val index = output.get().file("META-INF/micronaut-graphql-tools/schema-index.txt").asFile
        index.parentFile.mkdirs()
        index.writeText(schemaFiles.files.map { "graphql/" + it.relativeTo(file("src/main/resources/graphql")).invariantSeparatorsPath }
            .sorted().joinToString("\n", prefix = "# GraphQL schema files\n", postfix = "\n"))
    }
}
sourceSets.main { resources.srcDir(schemaIndex) }
```

Without the index a wildcard location fails the startup of the native image, naming the resource. On the JVM, a jar that
has no directory entries cannot be listed. For `classpath*:` the index of such a jar is read next to the roots that were listed, and the files of both are merged; for `classpath:` the index is used only when no classpath entry has the directory.

## Binding, types, scalars and customization

These topics are in the guide, in `src/main/docs/guide`:

| Topic | Guide page |
| --- | --- |
| Where a field gets its value, property or type resolver precedence, renaming, resolver scopes | [binding.adoc](src/main/docs/guide/binding.adoc) |
| Resolver contracts and the marker interfaces | [resolverContracts.adoc](src/main/docs/guide/resolverContracts.adoc) |
| Reactive return types, enums, unions, interfaces and the schema mapping dictionary | [typeSystem.adoc](src/main/docs/guide/typeSystem.adoc) |
| Arguments, input classes and `ArgumentValue` | [inputs.adoc](src/main/docs/guide/inputs.adoc) |
| Standard and custom scalars | [scalars.adoc](src/main/docs/guide/scalars.adoc) |
| Customizers and replacing the `GraphQL` bean | [customization.adoc](src/main/docs/guide/customization.adoc) |
| Instrumentation beans | [observability.adoc](src/main/docs/guide/observability.adoc) |

## Return types

The declared return type of a field (a resolver method or a bean property) may wrap the mapped class in the
containers graphql-java resolves at runtime, outermost first and each at most once:

- `CompletionStage<T>`, for the field itself only;
- `Publisher<T>` (`org.reactivestreams.Publisher`, any subtype such as `Flux`), required on fields of the subscription
  type and allowed nowhere else; the wrappers below then apply to each event;
- `DataFetcherResult<T>`, for the field or each list element; its errors and local context are applied;
- `Optional<T>`, for the field or each list element, and only where the GraphQL type is nullable (an empty
  `Optional` becomes `null`);
- `OptionalInt`, `OptionalLong` or `OptionalDouble`, under the same rules, mapped to `Int`, `Long` or `Float`.

The mapping then checks `T`. Anything else fails at startup with `[unsupported-return-type]`. In particular:

- `List<CompletionStage<T>>` is rejected: graphql-java does not wait for futures inside a list, and the client would
  receive the future's `toString()`. Return `CompletionStage<List<T>>` instead.
- `Optional<T>`, `OptionalInt`, `OptionalLong` and `OptionalDouble` on a non-null type (`String!`, `[String!]`) are
  rejected.
- A field of the subscription type must return a `Publisher` (optionally inside a `CompletionStage`); otherwise the
  startup fails (graphql-java would fail only on the first subscription request). A `Publisher` on any other field,
  or inside a list, is rejected.
- `DataFetcherResult<Publisher<T>>` is not supported on a subscription field, although graphql-java would unwrap it:
  return the `Publisher` and, if needed, a `DataFetcherResult` per event.

## Resolver contracts

`@GraphQLRootResolver` and `@GraphQLTypeResolver` are inherited (`@Inherited`). They may sit on an interface, for
example one emitted by a code generator, and a bean that implements it is a resolver without a GraphQL annotation of
its own. The annotations carry no bean stereotype: a class that carries one, extends an annotated class or implements
an annotated interface is a bean only when it has a scope annotation of its own, such as `@Singleton`. A class without
one is not a bean, so a fake or a second implementation does not become a second resolver by accident, and the
compile-time checks report a contract that no bean class implements. Every bean that implements the contract is a
resolver, so a hand-written fake or delegating adapter that implements it and is a bean is registered too: annotate it
with `@Replaces(<original>.class)` (`@Requires(env = "test")` alone does not help, because both beans are active in the
test environment). Micronaut AOP advice (`@Around`) on the implementation still applies.

**Marker interfaces.** `QueryResolver`, `MutationResolver`, `SubscriptionResolver` and `TypeResolver<T>` (package
`io.micronaut.graphql.tools.resolver`) are the contracts without an annotation. `interface BookTitleResolver extends
TypeResolver<Book>` binds to `Book` with no class literal, and the root marker fixes the operation type: the methods of
a `MutationResolver` bind fields of the mutation type only, so one method name can serve a field of each operation type.
Only the methods that implement a method of a contract interface (same name, same number of parameters) bind, each to the
operation of its own contract: a public helper of the implementation binds
nothing and does not fail strict mode. A class that implements a marker directly binds all its public methods. The
processor records the operation, the source type and the contract methods in the annotation metadata of the
implementation (the build needs `micronaut-graphql-tools-processor`, otherwise the startup fails with
`[type-resolver-contracts-not-found]`). The guide page Resolver Contracts has the examples.

```java
public interface BookTitleResolver extends TypeResolver<Book> {
    String title(Book book);
}

@Singleton
public class BookTitles implements BookTitleResolver {
    @Override
    public String title(Book book) { ... }
}
```

```java
@GraphQLTypeResolver(Book.class)
public interface BookLabelResolverContract {
    @GraphQLSchemaCoordinate("Book.label")
    String label(Book book);
}

@Singleton
public class BookLabelResolver implements BookLabelResolverContract {
    @Override
    public String label(Book book) { ... }
}
```

`@GraphQLSchemaCoordinate` makes the contract methods executable: Micronaut carries the method annotations from the
contract method over to the implementation. A method with a coordinate binds the field its coordinate names and no
other: a type resolver method only on that object type (or, for an interface field, on the implementations of that
interface), a root resolver method only on that root operation type.
Methods of beans without coordinates are matched by name. The coordinate is also checked at startup, never trusted:

- On a bean where at least one method carries a coordinate, only those methods bind. Other public methods (helpers)
  are not candidates.
- The startup fails with `[unknown-schema-coordinate]` (or `[coordinate-on-non-resolver]`), listing every problem, when a coordinate is not of the
  form `Type.field`, names a type or field the schema does not have or a type that is neither an object type nor an
  interface, names a non-root type on a `@GraphQLRootResolver` or a root operation type on a
  `@GraphQLTypeResolver`, or sits on a bean that is neither. The method may have any name: the coordinate decides, see
  [Binding a field to a differently named member](src/main/docs/guide/binding.adoc).
- It also fails when the method does not end up bound to exactly its coordinate: for example the type is mapped to a
  class that the `@GraphQLTypeResolver` does not resolve (a method that declares its coordinate wins over a property of
  the mapped class, see [Property or type resolver](src/main/docs/guide/binding.adoc)). A coordinate whose type
  is unreachable and allowed (see [Configuration](#configuration)) is not bound; with `unbound-resolver-method` at `fail` (the default)
  it is an error, otherwise it is logged as a warning.

**Root operation types.** Root resolver methods are scoped per root operation type (`Query`, `Mutation`,
`Subscription`). A method with `@GraphQLSchemaCoordinate("Subscription.status")` binds only that field, so the same
field name on two operation types binds two different methods:

```java
@Singleton
@GraphQLRootResolver
public class StatusQuery {
    @GraphQLSchemaCoordinate("Query.status")
    public String status(String id) { ... }
}

@Singleton
@GraphQLRootResolver
public class StatusSubscription {
    @GraphQLSchemaCoordinate("Subscription.status")
    public Publisher<String> status(String id) { ... }
}
```

A root resolver method of a bean without coordinates binds the field of its name on every root operation type, as it
always did; give it a coordinate to scope it to one. When that makes one method serve several operation types (a
`ping()` for `Query.ping` and `Mutation.ping`), [strict mode](#strict-mode), on by default, fails the startup
(`root-method-several-operations`); setting that check to `warn` or `ignore` allows it. When more than one method can bind one root field (two beans with
a method of that name, two methods with one coordinate, or a coordinate method and a method of a bean without
coordinates), the startup fails with `[multiple-methods-found]` naming the field and every method. Before, the
first bean found won, which depended on the order Micronaut returned the beans in. There is no option to restore that.

**Inheritance from classes.** The annotations are inherited from superclasses too, so a bean that extends an annotated
class is a resolver as well: with the annotated class and a `@Singleton` subclass, both are registered. When both
then bind the same field, the startup fails with `[multiple-methods-found]` naming both classes. Annotate only the
class that should be the resolver, or annotate the subclass with `@Replaces(<original>.class)`: the original is then
dropped and only the subclass binds. `@Requires(env = "test")` alone does not help, because both beans are active in the
test environment. The same applies to two beans of different packages that bind one root field: the message names the
operation field, each bean, and both fixes (`@Replaces`, or `@GraphQLSchemaCoordinate` when the methods serve different
operation types).

**Several contracts on one bean.** A bean may implement type resolver contracts for different classes. The
annotation metadata keeps only one of the inherited values, so the class hierarchy is read to find every contract, and
each method binds for the class of its first parameter (the source object). A public method whose first parameter is
none of those classes fails the startup with `[ambiguous-type-resolver]`, naming the bean and every contract (on a
bean that uses `@GraphQLSchemaCoordinate`, only methods with a coordinate count).

The contracts are found at compile time: the visitor of `micronaut-graphql-tools-processor` walks the superclasses and
interfaces of each bean class and writes the contracts into the annotation metadata of the bean (the internal
`@ResolverContracts`), and the startup reads them from the bean definition. It uses no reflection, so a bean binds the
same way on the JVM and in a native image. The visitor writes the contracts also with
`micronaut.graphql.tools.processor.checks=false`. A bean that is a type resolver only through an inherited annotation,
in a build that does not run the processor, fails the startup with `[type-resolver-contracts-not-found]`, which names
the processor and where to add it (see [Compile-time checks](#compile-time-checks)). Add the processor,
or annotate the bean class directly: a bean annotated directly binds without the processor (but a bean that is
annotated directly and also inherits another contract needs the processor to see the inherited one).

## Resolver scopes

A resolver is an ordinary bean, and its scope decides the instance the same way it does for any other injection point.

| Scope | Behaviour |
|---|---|
| `@Singleton` (the default of the resolver annotations) | One instance, looked up once at binding and invoked directly on every call, with no lookup per call. |
| `@Prototype` | A new instance for every call of a field, looked up when the field is fetched. |
| `@RequestScope` | One instance per HTTP request. The bean is a scoped proxy that is looked up once at binding, and the proxy picks the instance of the current request. |
| `@Refreshable` | A scoped proxy as well: it follows a refresh without a new schema build. |

`@RequestScope` needs a current HTTP request. A field fetched outside one, such as a subscription over a WebSocket
connection, fails with `IllegalStateException: No request present`. Use a singleton resolver there, and pass per-request
state through the GraphQL context. Kotlin classes of a proxied scope must be `open`, as for any Micronaut proxy.

Each schema build binds from the same resolver registry, so a second `GraphQLSchema` built in one context (for example with
`ApplicationContext.createBean(GraphQLSchema.class)`) binds the same fields as the first.

## Startup errors

The schema is bound when the application context starts, whether the library or the application creates the
`GraphQL` bean: a field that cannot be bound fails the startup (of the `ApplicationContext` and of the embedded server),
and the first request does no binding work. `micronaut.graphql.tools.eager-init: false` binds it when the first bean
that needs the `GraphQLSchema` is created instead, for tests that expect the failure on a bean lookup. Every field that
cannot be bound is reported; the binding does not stop at the first one. A single failure is thrown as a `SchemaBindingException` with its own code (`[field-not-bound]`,
`[multiple-methods-found]`, `[incorrect-class-mapping]`, ...). Several are thrown together as one `SchemaBindingException`
with the code `[binding-failures]`, sorted by schema coordinate: `getFailures()` lists them, the first is the cause and the
others are suppressed exceptions. An unexpected exception of one field (for example an unsupported type definition) is
reported like the others, named by the field's coordinate, so it does not hide them. The checks that need a complete
binding (unreachable types, schema coordinates, strict mode) run only when no field failed, and then they report
together: when more than one finds a problem, the startup fails once with `[binding-failures]` that lists each
finding under the code of its check (`[unreachable-type]`, `[unknown-schema-coordinate]`, `[strict-findings]`), in that order;
a single finding is thrown as it is, with its own code.

```
[binding-failures] The schema cannot be bound: 3 errors.
  1) [unreachable-type] The schema defines types that no field reachable from a root operation type returns, so nothing binds them: Draft (type).
     Bind them through a field, remove them from the schema, list them in micronaut.graphql.tools.allow.unreachable, or set micronaut.graphql.tools.checks.unreachable-type to warn or ignore.
  2) [unknown-schema-coordinate] Invalid @GraphQLSchemaCoordinate declarations:
       1) @GraphQLSchemaCoordinate("Book.summary") on example.AuthorResolver summary(example.Author author): the method is not bound to Book.summary, ...
  3) [strict-findings] The strict checks found problems in the binding of the schema:
       1) [unbound-resolver-method] The resolver method example.BookQuery#helper(java.lang.String) binds no schema field. Remove it, ...
       Fix them, allow them in micronaut.graphql.tools.allow.findings, or set micronaut.graphql.tools.checks.<code> to warn or ignore.
```

Each failure names the schema coordinate (`Type.field`, `Type.field(argument:)` or `InputType.field`) and where it is
declared, as graphql-java parsed it (the source name appears when the schema was parsed with one, for example through
`MultiSourceReader`), followed by the candidates that were considered:

```
The method `missing` not found in any root resolvers: [example.BookQuery].
  GraphQL coordinate: Query.missing (books.graphqls, line 9, column 3)
```

`getMappingContext().getCoordinate()` and `getMappingContext().getSourceLocation()` give the same information to code.

### Error codes

Every startup exception extends `SchemaBindingException`, and every message starts with a code in square brackets
(`[field-not-bound] The method ...`). `SchemaBindingException#getCode()` returns the same code as a `BindingCode`, the one
public type of the `graphql-tools-binding` module. A code is a lower-case kebab-case word and does not change after a
release. The word after `!` in a [binding manifest](#binding-manifest) line, the name of a strict check and the code of
the compile-time diagnostic are the same code. The ten strict checks have these codes: `unbound-resolver-method`,
`root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `nullable-primitive`,
`unreachable-type`, `unreachable-value-type`, `unused-dictionary-entry`, `interface-read-through-implementation` and
`unwired-directive`; a precedence ambiguity has `precedence-ambiguity`, and every other failure has the code of its
kind (`argument-mismatch`, `unsupported-return-type`, `scalar-mapping-not-provided`, ...). The error reference
(`src/main/docs/guide/exceptions.adoc`) lists every code with its meaning and whether the compile-time check reports it.

## Binding manifest

The `BindingManifest` bean says what the schema is bound to: one line per schema coordinate, naming the Java member
that serves it, in a stable, versioned text format (`graphql-binding-manifest 2`) that can be checked into a repository
and diffed. Recording is opt-in: set `micronaut.graphql.tools.manifest.enabled: true` to record it while the schema is
bound (without it nothing is recorded and getting the bean fails with a message saying so). Getting the bean binds the
schema first. With `micronaut.graphql.tools.manifest.path` also set it is written
at startup to that path (directories are created), which binds the schema at startup. The format is specified in the guide page "Binding Manifest" (`src/main/docs/guide/manifest.adoc`): the grammar,
the line kinds, the sorting, the escape of non-ASCII characters and the schema hash, which does not depend on the order
of the schema files or of the extensions.

With Micronaut Management on the classpath and the manifest recorded, the read-only endpoint `graphql-binding`
(`GET /graphql-binding`) serves it as JSON once `endpoints.graphql-binding.enabled` is `true` (it is off by default): the `header` lines and the `lines` of the file output.
The standard `endpoints.graphql-binding.sensitive` key applies. Without Micronaut Management
nothing is registered.

```
# graphql-binding-manifest 2
# binder: micronaut-graphql-tools 1.0.0
# graphql-java: 21.3
# schema-sha256: 207c5aaec4894a775f03ae20eaa6b97ba4293ed6608936aa65c812af3a469469
!unreachable-type Legacy
Book -> object ; example.data.Book ; - ; -
Book.title: String! -> property ; example.data.Book#getTitle() ; java.lang.String ; java.lang.String
Query.allBooks: [Book!]! -> root ; example.graphql.query.AllBooksQuery#allBooks() ; java.util.List<example.data.Book> ; java.util.List<example.data.Book>
String -> scalar ; String ; - ; -
```

## Strict mode

The strict checks fail the startup on bindings that work but are probably mistakes. There are ten, each with a code
(see [Codes](#error-codes)), and each finding is also a `!` line of the [binding manifest](#binding-manifest). Every
check has a severity, set under `micronaut.graphql.tools.checks`:

| Severity | What the check does with a finding |
|---|---|
| `fail` (the default of every check) | Fails the startup. All findings of the check are reported together. |
| `warn` | Logs one warning per check that lists the findings, and goes on. The findings are in the manifest. |
| `ignore` | Skips the check: it does no work and writes no manifest line. |

```yaml
micronaut:
  graphql:
    tools:
      checks:
        unreachable-value-type: warn
        unwired-directive: ignore
```

`strict` is the shorthand for the checks that have no entry: `true` (the default) leaves every check at `fail`, and
`false` makes every check `warn`, including `unreachable-type` for object, interface and union types. An explicit
`checks.<code>` entry wins over both. An unknown code or a value that is not `fail`, `warn` or `ignore` fails the startup
with `[invalid-check-setting]`. The severity settings apply to the startup only. `precedence` is independent of
`strict` and is not a check: a precedence ambiguity under `precedence: fail` stays a binding failure.

The compile-time checks (`micronaut-graphql-tools-processor`) never read these settings. Five checks also run at
compile time (`unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`,
`unmapped-input-property`, `nullable-primitive`); relaxing one at startup does not relax the compile error.

- **`unreachable-type`**: an object, interface or union type that no field reachable from a root operation type
  returns, and so nothing binds. At `warn` or `ignore` the type stays in the schema with graphql-java's default data
  fetchers. The enum, input and scalar types its fields and arguments use are covered with it.
- **`unreachable-value-type`**: an enum, input or scalar type that no bound field, argument or input field uses. A
  type that an argument of a directive definition uses, directly or through input fields, is reached. A type that an
  allowed or relaxed unreachable type uses is covered, and the types those input types use in turn, so
  `allow.unreachable: [Legacy]` is enough for `type Legacy { since: Long era: Era }`.
- **`unbound-resolver-method`** (`!unbound-resolver-method Class#method(...)`): every executable method of a
  `@GraphQLRootResolver` or `@GraphQLTypeResolver` bean, which Micronaut makes of its public methods, must bind a
  field. On a bean that uses `@GraphQLSchemaCoordinate`, only the annotated methods count, so a contract
  implementation may keep public helpers. Overrides of `Object`'s methods (`toString()`, `equals`, `hashCode()`) do
  not count. A coordinate on a type that is unreachable and allowed is such a method too. A type resolver method that
  `precedence: property-first` leaves unbound is accounted for by the `!precedence-ambiguity` line instead.
- **`root-method-several-operations`** (`!root-method-several-operations Class#method(...)`): a root resolver method
  without a coordinate that binds the field of its name on more than one root operation type (`Query.ping` and
  `Mutation.ping`). Give each operation type its own method with `@GraphQLSchemaCoordinate`.
- **`unbound-enum-constant`** (`!unbound-enum-constant Enum#CONSTANT`): a Java enum constant without a schema value
  cannot be returned (graphql-java fails to serialize it) and never arrives as an argument.
- **`unused-dictionary-entry`** (`!unused-dictionary-entry Type = Class`): a dictionary entry for a type that is not
  bound, except for allowed unreachable types.
- **`interface-read-through-implementation`** (`!interface-read-through-implementation Type`): an object type mapped
  to a Java interface that has no bean introspection of its own is read through the introspection of its one
  `@GraphQLType(TheInterface.class)` class, so a getter only the implementation declares binds silently (see
  [Java interfaces as object types](src/main/docs/guide/typeSystem.adoc)). Annotate the interface with `@GraphQLType` or
  `@Introspected`.
- **`unmapped-input-property`** (`!unmapped-input-property InputClass#property`): a property the class can set
  (through a setter or the constructor) that no request can ever set. They are reported in one
  `[unmapped-input-property]` (see [Input classes and input types](src/main/docs/guide/inputs.adoc)).
- **`nullable-primitive`** (`!nullable-primitive Query.field(argument:)`): an argument or input class property declared
  as a primitive (`int`, `boolean`, `double`, ...) for a nullable built-in scalar (`Int`, `Boolean`, `Float`,
  `Long`, ...): a `null` (omitted or explicit) cannot reach it, so it arrives as the primitive's default value or fails
  the request. Use the wrapper class or make the GraphQL type non-null. For a custom scalar this fails in every mode.
  A primitive for a non-null type and a primitive return type are fine.
- **`unwired-directive`** (`!unwired-directive name`, one line per directive): a directive the schema applies that no
  `SchemaDirective` bean, `GraphQLRuntimeWiringCustomizer` or `resolver-directives` entry implements and that
  `allow.inert-directives` does not list (see [Schema directives](#schema-directives)); a directive applied only on
  `schema { }` or on a directive argument is reported even when a wiring names it, unless `allow.inert-directives` or
  `allow.schema-customizer-directives` lists it. They are reported in one `[unwired-directive]` failure, which lists
  where each is applied.

The findings of `unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`,
`unused-dictionary-entry` and `interface-read-through-implementation` are reported in one `[strict-findings]` failure.
Every schema field must be bound in every mode (`[field-not-bound]`).

### Allow lists

The `allow` settings accept findings without relaxing the whole check. Every entry is validated at startup, and one
that no longer applies fails it, so the lists cannot go stale:

- `allow.unreachable`: names of unreachable types (object, interface, union, enum, input or scalar). Each must be an
  unreachable type of the schema. The type is accepted with the types it uses.
- `allow.inert-directives`: directives that have no effect at runtime; `*` makes a pattern (`fed*`) that must match a
  declared directive without a wiring. Each exact name must be declared, not built in, without a wiring and not a
  `resolver-directives` entry.
- `allow.schema-customizer-directives`: directives that a `GraphQLSchemaCustomizer` handles on the `schema` definition
  or on directive arguments, where graphql-java calls no wiring. Each must be declared and not built in.
- `allow.findings`: lines of the binding manifest, exactly as the manifest writes them, for a finding you cannot fix
  or annotate. Paste the `!` line:

  ```yaml
  micronaut:
    graphql:
      tools:
        allow:
          findings:
            - "!unbound-resolver-method example.BookQuery#helper(java.lang.String)"
  ```

  The finding then neither fails the startup nor is logged, and the manifest does not list it. An entry that is not
  the line of a check, whose check is `ignore`, or that matches no finding of the startup fails with
  `[invalid-allow-findings]`. A precedence ambiguity is not a check and cannot be allowed here.

Migrating an application that fails the checks: set `strict: false`, `precedence: resolver-first` and
`manifest.enabled: true` and a `manifest.path`, start it, then fix, annotate (`@GraphQLIgnore`) or allow each `!` line of the manifest, and
remove `strict: false` again.

### Escape hatches

A member that binds no field on purpose does not need to relax a check for the whole application:

- **`@GraphQLIgnore`** (package `io.micronaut.graphql.tools.annotation`) on a resolver method, a getter or property of a
  type or input class, or an enum constant. The member is not reported as unbound (`!unbound-resolver-method`), and nothing binds it: an
  ignored method does not bind the field of its name, and an ignored getter does not take part in `precedence`, so a
  type resolver method may bind that field without an ambiguity. A schema field that only ignored members could
  bind fails as any unbound field does. On an input class property it suppresses the finding for a property that the
  input type does not define. On an enum constant it suppresses the finding for a constant without a schema value; the
  enum needs a bean introspection (`@Introspected`), from which the annotation is read, never by reflection.
  Every ignored member the binding meets is a `~ignored Class#member` line of the
  [binding manifest](#binding-manifest), so a review can see what was set aside. Use it sparingly: it hides the
  mistake that the check exists to find.
- **Lifecycle methods are not resolver methods.** A method annotated with `@EventListener`, `@Scheduled` or
  `@PreDestroy` on a resolver bean is not reported as unbound. It still binds a field if it has that field's name.
- **`allow.findings`** accepts one finding by its manifest line, see [Allow lists](#allow-lists).

The compile-time visitor in `micronaut-graphql-tools-processor` reads the same annotations and applies the same rules, so a build and the startup agree.

`strict: false` does not change `precedence`: with the default `fail` precedence, a field that both a getter and a
type resolver can bind still fails the startup (`[precedence-ambiguity]`).

## Configuration

| Property | Values (default first) | Meaning |
|---|---|---|
| `micronaut.graphql.tools.schema.locations` | list of locations (`classpath:graphql/**/*.graphqls`) | Where the schema files are read from when the application has no `TypeDefinitionRegistry` bean: `classpath:`, `classpath*:` (dependency jars too) or `file:`, then an Ant-style path. See [Schema discovery](#schema-discovery). |
| `micronaut.graphql.tools.precedence` | `fail` \| `property-first` \| `resolver-first` | What binds a field that both a member of its Java type and a type resolver method can bind, see [Property or type resolver](src/main/docs/guide/binding.adoc). `fail` (default) fails the startup; a field that carries a resolver directive, or whose resolver method declares its coordinate, is not ambiguous. |
| `micronaut.graphql.tools.resolver-accessor-prefixes` | `false` \| `true` | Whether a resolver method named `getX` or `isX` also binds the field `x`, see [Binding a field to a differently named member](src/main/docs/guide/binding.adoc). |
| `micronaut.graphql.tools.resolver-directives` | list of directive names (empty) | A field carrying one of these directives is bound by a type resolver method, and fails the startup if none binds it. Each must be declared in the schema. |
| `micronaut.graphql.tools.strict` | `true` \| `false` | The shorthand for the severity of every check that has no `checks` entry: `true` leaves every check at `fail`, `false` makes every check `warn` (the findings are logged and listed in the manifest). It does not change `precedence`. See [Strict mode](#strict-mode). |
| `micronaut.graphql.tools.checks` | map of check code to `fail` \| `warn` \| `ignore` (empty) | The severity of single checks, for example `checks.unwired-directive: ignore`: `unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `nullable-primitive`, `unreachable-type`, `unreachable-value-type`, `unused-dictionary-entry`, `interface-read-through-implementation`, `unwired-directive`. An entry wins over `strict`. An unknown code or severity fails the startup. The five compile-time checks (`unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `nullable-primitive`) are not relaxed at compile time. |
| `micronaut.graphql.tools.eager-init` | `true` \| `false` | Bind the schema when the application context starts, so that a broken binding fails the startup (also when the application defines its own `GraphQL` bean) and the first request does no binding work. `false` binds it when the first bean that needs the `GraphQLSchema` is created. |
| `micronaut.graphql.tools.execution.data-loader-statistics` | `false` \| `true` | Whether the library counts the batches that each batch loader bean dispatches in an execution (`DataLoaderBatchStatistics` in the GraphQL context); the [Micrometer module](#observability) turns the counts into DataLoader metrics. Off by default: counting adds two atomic increments to each batch. |
| `micronaut.graphql.tools.execution.timeout` | duration, default none | How long an operation may take until its result is ready. When it runs out the client gets an `EXECUTION_TIMEOUT` error and the execution is cancelled, so later async fields abort; a running resolver is not interrupted. The timer exists only when the key is set. |
| `micronaut.graphql.tools.protection.document-cache-size` | `1000` | How many parsed and validated documents are kept, keyed by the query text (least recently used); `0` turns the cache off. |
| `micronaut.graphql.tools.protection.max-depth` | `0` (no limit) | The deepest selection nesting that an operation may have; a deeper one is rejected with a GraphQL error. |
| `micronaut.graphql.tools.protection.max-complexity` | `0` (no limit) | The highest complexity (every selected field counts 1) that an operation may have; a higher one is rejected with a GraphQL error. |
| `micronaut.graphql.tools.protection.introspection` | `true` \| `false` | Whether `__schema` and `__type` can be queried; off, they fail validation. |
| `micronaut.graphql.tools.manifest.enabled` | `false` \| `true` | Record the [binding manifest](#binding-manifest) while the schema is bound, so that the `BindingManifest` bean can be read. Off by default: nothing is recorded. The checks run the same either way. |
| `micronaut.graphql.tools.manifest.path` | file path | Write the recorded manifest to this file at startup (this binds the schema at startup). |
| `micronaut.graphql.tools.allow.unreachable` | list of type names (empty) | Unreachable types that the checks accept; they are logged at INFO. Every entry must be an unreachable type of the schema (object, interface, union, enum, input or scalar), otherwise the startup fails, so the list cannot go stale. An allowed type also allows the enum, input and scalar types that its fields and arguments use, and that those input types use in turn (transitive); list only the type itself. A type that only a type that is not allowed uses stays unreachable. |
| `micronaut.graphql.tools.allow.inert-directives` | list of directive names, without `@` (empty) | Directives the schema may apply without a wiring, because they have no effect at runtime; they are logged at INFO. An entry with `*` is a pattern (`fed*`) that must match at least one declared directive without a wiring. Every other applied directive needs a wiring (see [Schema directives](#schema-directives)). Every exact entry must be a directive the schema declares, that is not built in, that has no wiring and that is not a `resolver-directives` entry, otherwise the startup fails in every mode, so the list cannot go stale. |
| `micronaut.graphql.tools.allow.schema-customizer-directives` | list of directive names, without `@` (empty) | Directives that a `GraphQLSchemaCustomizer` handles on the `schema` definition or on directive arguments, where graphql-java calls no wiring (see [Schema directives](#schema-directives)). Every entry must be a directive the schema declares that is not built in and that it applies on the schema definition or on a directive argument, otherwise the startup fails in every mode. |
| `micronaut.graphql.tools.allow.findings` | list of manifest lines (empty) | Findings to accept, as `!` lines of the [binding manifest](#binding-manifest), for example `"!unbound-resolver-method example.Query#helper(java.lang.String)"`. Such a finding does not fail the startup, is not logged and is not in the manifest. An entry that is not the line of a check, whose check is `ignore`, or that matches no finding fails the startup. See [Allow lists](#allow-lists). |

```yaml
micronaut:
  graphql:
    tools:
      allow:
        unreachable:
          - LegacyReport
```

## Compile-time checks

The `micronaut-graphql-tools-processor` module registers a Micronaut `TypeElementVisitor` that reads the schema files while the
application compiles and fails the build on drift that the startup would report, with the same code and the same schema
coordinate. The build setup for Gradle and Maven (Java, Kotlin with KSP or kapt, Groovy) is on
[Set Up the Build](src/main/docs/guide/compileTime/setup.adoc), the options on
[Options](src/main/docs/guide/compileTime/options.adoc), the support matrix and the incremental builds on
[Build Manifest](src/main/docs/guide/compileTime/buildManifest.adoc), and what is checked at compile time and what
only at startup on [Limits](src/main/docs/guide/compileTime/limits.adoc); the section starts at
[Compile-Time Verification](src/main/docs/guide/compileTime.adoc). The error codes are in the
[error reference](src/main/docs/guide/exceptions.adoc).

### The strict schema-build test

What the processor cannot decide passes the compilation and fails the startup: a union member or interface
implementation without a `SchemaMappingDictionary` entry, or mapped to a class that does not implement the Java
interface the field declares; a class that is the Java type of two members of one union (the dictionary
decides which class a member type has, so the processor cannot check "one member per class"); a custom scalar without a `GraphQLScalarMapping` bean; directive wirings. So that this
fails the build too, and not the first deployment, the application keeps one test that builds the schema in strict
mode (the default), with the application's real beans:

```java
@MicronautTest(startApplication = false)
class GraphQLSchemaBuildTest {

    @Inject
    BindingManifest bindingManifest; // getting it builds the schema

    @Test
    void theSchemaBuilds() {
        assertFalse(bindingManifest.getLines().isEmpty());
    }

}
```

Values a resolver returns are not checked by either: a `null` for a non-null field and the class of an object that a
union or interface resolves are errors of the request, reported by graphql-java.

## Customizing the schema

Three kinds of beans change what the library builds. Each kind may have any number of beans; they run lowest order
first, by `@Order` on the bean class or factory method, or by `Ordered#getOrder()` if the bean implements `Ordered`.

| Bean type | Receives | Runs |
|---|---|---|
| `GraphQLTypeDefinitionRegistryCustomizer` | a copy of the application's `TypeDefinitionRegistry` bean (the bean itself stays unchanged) | before the binding: types and fields added here need a binding like any other |
| `GraphQLRuntimeWiringCustomizer` | the `RuntimeWiring.Builder` that holds the library's wiring | after the binding, before the schema is generated |
| `GraphQLSchemaCustomizer` | the generated `GraphQLSchema`; returns the schema to use | last |

A `GraphQLRuntimeWiringCustomizer` may only **add** (scalars, directive wirings, field visibility, ...). If it replaces a data
fetcher, default data fetcher, type resolver or scalar that the library registered, or the wiring factory, or puts a
data fetcher or type resolver for a field or type the library wired into a code registry (`builder.codeRegistry(...)`,
which graphql-java lets win), the startup fails with `[runtime-wiring-override]`, which names every replacement
and the customizer that made it. The check runs only when the application has a `GraphQLRuntimeWiringCustomizer` bean; the
library's own directive wirings are not checked, and without a customizer the startup does no check work. Code registry entries for fields the library does not wire, and enum value
providers, are allowed.
(On graphql-java 25.2 the builder itself already rejects the first replaced default data fetcher, type
resolver or scalar with `StrictModeWiringException`; 21.5 and 22.4 accept them silently. A second data fetcher for a
field the library wired is reported as `[runtime-wiring-override]` on every line. On some graphql-java lines `RuntimeWiring.Builder#type` for a
type the library wired clears that type's default data fetcher; the library puts it back, so calling `type` to add a
field fetcher is not a replacement.) To wrap data fetchers, use a
`GraphQLFieldFetcherDecorator` bean (see below) or a `SchemaDirective` bean (`named` or `everyElement`); to change the generated schema, use a `GraphQLSchemaCustomizer` (it replaces
`RuntimeWiring.Builder#transformer`, which graphql-java 25 removed). The library does not check what a
`GraphQLSchemaCustomizer` changes.

Which seam to use to change the schema: a `GraphQLRuntimeWiringCustomizer` to add wiring before the schema is generated, a
`GraphQLSchemaCustomizer` to change the generated schema as part of the library's own bean (recommended), and a
`BeanCreatedEventListener<GraphQLSchema>` when another module has to replace the `GraphQLSchema` bean. The library's
union and interface type resolvers read the schema of the execution (`TypeResolutionEnvironment#getSchema()`), so
they resolve against the replaced schema too, whatever seam produced it and in whatever order the beans run. Do not
keep a reference to the schema the library built; the data fetchers and type resolvers hold none.

```java
@Bean
@Singleton
@Order(10)
public GraphQLRuntimeWiringCustomizer hideInternalFields() {
    return builder -> builder.fieldVisibility(BlockedFields.newBlock().addPattern("Query.internal.*").build());
}
```

### Fields a customizer serves

A field that only a hand-written `DataFetcher` serves has no Java member, so the library would fail the startup with
`[field-not-bound]`. The customizer declares such a field by **claiming** it in `getClaimedFields()` and registers the
data fetcher in `customize`:

```java
@Singleton
class ServerVersionCustomizer implements GraphQLRuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> getClaimedFields() {
        return Set.of(FieldCoordinates.coordinates("Query", "serverVersion"));
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        builder.type("Query", type -> type.dataFetcher("serverVersion", new StaticDataFetcher("1.0")));
    }

}
```

- The library does not bind or check a claimed field, even when the Java type has a member of that name, and a data
  fetcher the customizer registers for it is not a replacement. Fields it does not claim stay protected: replacing one
  still fails with `[runtime-wiring-override]`.
- A claim must name a field of an **object** type of the schema (the fields of `extend type` included). A claim for a type
  or field the schema does not define, or for an interface, fails the startup with `[invalid-claimed-fields]`, which
  names every such coordinate and its customizer. To serve an interface field, claim it on each implementing object type.
- `[runtime-wiring-override]`, and the `[field-not-bound]` of a field added by `extend type`, name
  `getClaimedFields()` as the way out.
- `getClaimedFields()` is read once, at startup, before any field is bound. Claimed fields have a line in the
  [binding manifest](#binding-manifest) of kind `claimed`, naming the customizer.
- Types that only a claimed field returns are not reached by the binding: list them in `allow.unreachable`, or bind
  them through another field.
- Customizers are runtime beans, so the compile-time checks cannot see a claim; they report a field that nothing in the
  sources binds.

## Schema directives

The library applies an SDL directive wiring (`graphql.schema.idl.SchemaDirectiveWiring`) only through a
`SchemaDirective` bean:

- `SchemaDirective.named(name, wiring)` binds the wiring to **one** directive name; graphql-java calls it only for the
  elements that apply that directive (`RuntimeWiring.Builder#directive`). Two such beans for one name fail the
  startup with `[duplicate-directive-wiring]`, and a `GraphQLRuntimeWiringCustomizer` that replaces such a wiring fails
  it with `[runtime-wiring-override]`.
- `SchemaDirective.everyElement(names, wiring)` registers a wiring that graphql-java calls for **every** schema element
  and that decides itself what to do (`RuntimeWiring.Builder#directiveWiring`); the names say which directives it
  implements. It must return the element unchanged unless the element carries one of those directives, because a
  wiring that wraps the data fetchers of every element adds a call to each of them on every request. Prefer `named`
  when one directive is enough. Among the `everyElement` beans, these run lowest order first (`@Order` or `Ordered` on
  the `SchemaDirective` bean), so the first one wraps a data fetcher innermost.
- One directive may have either a `named` bean or any number of `everyElement` beans, not both: a `named` bean and an
  `everyElement` bean that lists the same name would apply the directive twice (two auth checks, a value transformed
  twice), so the startup fails with `[duplicate-directive-wiring]`, which names the directive and both wirings.
  Keep one of them: remove the `named` bean, or make the `everyElement` wiring stop handling that directive and then drop its name.
- All `named` wirings run before every `everyElement` wiring, so they are always innermost. `@Order` on a `named` bean
  has no effect: the library does not sort them, and they run in the order the SDL applies the directives to the
  element.

To wrap the data fetchers of selected fields, for example to sanitise arguments, prefer a `GraphQLFieldFetcherDecorator` bean to a
wrap-all `everyElement` wiring. The library asks each decorator once for each field at startup, and the decorator returns the
fetcher it received for a field it does not select, so unselected fields and trivial property fetchers keep their base
fetcher. A field without arguments and a property are not offered unless the decorator overrides
`decoratesFieldsWithoutArguments()` or `decoratesTrivialFields()`. The `FieldDescriptor` carries the coordinates, the
`FieldExecutionKind`, whether the field has arguments, the field executor, the annotation metadata and a description of the resolver. Decorators wrap
outermost: decorators (lowest order first), the async boundary, the `@ExecuteOn` hop, the adapters, the base fetcher. The
guide has the details.

A `SchemaDirectiveWiring` bean is **not** applied on its own: if neither a `SchemaDirective` bean wraps it nor a
`GraphQLRuntimeWiringCustomizer` registers it (`RuntimeWiring.Builder#directive` or `#directiveWiring`), the startup fails with
`[unwrapped-directive-wiring]`, which names the bean. The check runs after every customizer. A wiring therefore
never runs on every element just because it is a bean. To keep a wiring as a bean, inject it into the factory method of
its `SchemaDirective`. A singleton is matched by instance; a `@Prototype` bean (a new instance per injection point) by
its class, so it may be wrapped too. Because of that, an unwrapped `@Prototype` bean goes undetected when some wired
instance (of any bean) has the same class; the check then cannot tell the two instances apart.

**Every directive the schema applies must have a wiring**, or the startup fails with `[unwired-directive]`,
which lists every such directive and where the schema applies it. With the check `unwired-directive` at `warn` (or `strict: false`, see [Strict mode](#strict-mode)) the
same list is a warning instead; either way each such directive is an `!unwired-directive` line of the binding
manifest. A directive counts as wired if a `SchemaDirective` bean names it, if a `GraphQLRuntimeWiringCustomizer` registers a
wiring for it with `RuntimeWiring.Builder#directive`, or if `resolver-directives` lists it (the binding implements it). A
directive that is meant to have no effect at runtime (documentation, or enforced elsewhere) is listed in
`micronaut.graphql.tools.allow.inert-directives`, by name or with `*` as a pattern (`fed*`; a pattern must match a declared
directive that has no wiring, so it cannot go stale). `micronaut.graphql.tools.checks.unwired-directive` (`fail`, `warn` or `ignore`) sets what the check does; unset it
follows `strict`, and `ignore` skips the check, for schemas that apply many tooling directives.
`SchemaDirectiveWiring` has no hook for the schema definition or for directive arguments, so a wiring does not count
there: a directive applied on `schema { }` (coordinate `schema definition`) or on an argument of a directive definition
(`@name(arg:)`) fails the check unless `allow.inert-directives` lists it, or
`micronaut.graphql.tools.allow.schema-customizer-directives` lists it because a `GraphQLSchemaCustomizer` handles it. That list
takes exact names of declared directives that are not built in, and each must be applied on the schema definition or on a
directive argument: an entry that the schema never applies there fails the startup, so a stale entry is not silent. A directive that has a wiring may be applied at both
kinds of places only through `allow.schema-customizer-directives`, because `allow.inert-directives` rejects a directive that has a
wiring. Exempt: graphql-java's built-in directives (`@deprecated`, `@specifiedBy`, `@oneOf`,
`@include`, `@skip`), directives the schema declares but never applies, and directives it applies without declaring
them (graphql-java reports those). `SchemaDirective` names that the schema does not declare are logged at INFO and not
used, so a shared module may wire more directives than one schema applies.

```java
@Bean
@Singleton
public SchemaDirective upper(UpperCaseWiring upperCaseWiring) { // UpperCaseWiring may be a bean
    return SchemaDirective.named("upper", upperCaseWiring);
}
```

graphql-java-extended-validation plugs in as a wiring for every element that names its constraint directives, and a
`GraphQLTypeDefinitionRegistryCustomizer` can add their declarations unless the SDL declares them:

```java
@Bean
@Singleton
public SchemaDirective validation() {
    DirectiveConstraints constraints = DirectiveConstraints.newDirectiveConstraints().build();
    return SchemaDirective.everyElement(constraints.getConstraints().keySet(),
            new ValidationSchemaWiring(ValidationRules.newValidationRules().build()));
}

@Bean
@Singleton
public GraphQLTypeDefinitionRegistryCustomizer validationDirectives() {
    return registry -> registry.merge(DirectiveConstraints.newDirectiveConstraints().build().getDirectivesDeclaration());
}
```

Use extended-validation 21.0 with graphql-java 21.5, 22.0 with 22.4 and 24.0 with 26.2 (21.0 calls a
`GraphQLCodeRegistry` method that graphql-java 22.4 and later removed); the library's tests run all three.

## Observability

Two modules, each named after its technology, record request metrics and spans: `micronaut-graphql-tools-micrometer` records
metrics with Micrometer and `micronaut-graphql-tools-opentelemetry` records traces with OpenTelemetry. Neither library is a
dependency of its module: add the module of the one you use, with a `MeterRegistry` bean (Micronaut Micrometer) or an
`OpenTelemetry` bean (Micronaut Tracing). Whichever are on, the library adds one `Instrumentation` bean.

```groovy
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools-micrometer:1.0.0-M4")
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools-opentelemetry:1.0.0-M4")
```

- `graphql.request` is a timer of each request, tagged with `operation` (the operation name, capped at
  `micronaut.metrics.binders.graphql.max-operations`, default 100, the rest is `other`) and `outcome`
  (`success` or `error`). `micronaut.metrics.binders.graphql.enabled` and `micronaut.metrics.enabled` switch the metrics off.
- `graphql.dataloader.batches` and `graphql.dataloader.batch.keys` count the batches of each
  batch loader bean and the keys they loaded, tagged with `loader`, when `micronaut.graphql.tools.execution.data-loader-statistics` is `true`.
- Each request is one span, made with the OpenTelemetry `Instrumenter` API and named after the operation type (`query`,
  `mutation`, `subscription`; `micronaut.graphql.tools.tracing.add-operation-name-to-span-name` adds the operation name). It has the
  OpenTelemetry GraphQL attributes and is current in the propagated context, so a `@NewSpan` method that a resolver calls is
  its child. `micronaut.graphql.tools.tracing.fields` lists the fields that also get a span (`Type.field` or `Type.*`); with
  the default empty list no field has a span, and a property read never has one.
- `micronaut.graphql.tools.tracing.enabled` and `micronaut.otel.enabled` switch the tracing off.
- A timer or a span for one of your resolvers is `@Timed` or `@NewSpan` on the method.

The guide page on observability has the details, the span layout, `@NewSpan` and `@Timed` on resolvers, and the cost.

## File upload

The `micronaut-graphql-tools-upload` module implements the GraphQL multipart request specification for Micronaut 4: the
`Upload` scalar and a `multipart/form-data` route on the path of the GraphQL endpoint.

```groovy
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools-upload:1.0.0-M4")
```

Declare `scalar Upload` in the schema and use it for an argument or an input object field; a resolver declares the
parameter or the property as `CompletedFileUpload` (or `CompletedPart`). The route runs the operation through the
library's invocation, so the resolver runs on the executor of the library, not on the event loop. An invalid request is a
`400` with an error whose `extensions.code` is `INVALID_MULTIPART_REQUEST`. `micronaut.graphql.tools.upload.enabled`
(default `true`) turns the module off. The guide page on file upload has the details.

## Testing

The `micronaut-graphql-tools-testing` module runs a GraphQL document in the process of a test, without a server or an HTTP
client.

```groovy
testImplementation("io.micronaut.graphql.tools:micronaut-graphql-tools-testing:1.0.0-M5")
```

A `@MicronautTest` class injects `GraphQLTester`, builds a request with variables, context values and headers, and
asserts on the `GraphQLTestResponse`: `tester.request(document).variable("id", "b1").execute().assertNoErrors().assertPath("book.title", "Dune")`.
The document goes through the library's invocation, so the context contributors and the other beans run as for a request.
The module is test-time code and has no native test. The guide page on testing has the details.

## Security

The `micronaut-graphql-tools-security` module integrates Micronaut Security. Micronaut Security stays the application's own
dependency; the core library has none.

```groovy
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools-security:1.0.0-M4")
implementation("io.micronaut.security:micronaut-security")
```

A resolver declares `Principal` or `Authentication` as a parameter and gets the principal of the request, also in a field
that runs after an async hop and over WebSocket (the principal of the upgrade request). `@Secured` on a resolver method
guards its field: an anonymous call gets an `UNAUTHENTICATED` error of the field, a call without the role a `FORBIDDEN`
one, and a field without `@Secured` keeps its fetcher. A `RootFieldsHook` bean sees the root fields of an operation after
parsing, so an application need not parse the body in a filter. `micronaut.graphql.tools.security.enabled` (default `true`)
turns the module off. A resolver also reads the HTTP request and changes the response (a header, a cookie) with the
`HttpRequest` and `MutableHttpResponse` parameters. The guide pages on security and on the HTTP request and response have
the details.

## Error handling

The library installs a `DataFetcherExceptionHandler` that hides the message of an exception that a resolver throws: the
client gets `Internal server error` with `extensions.code` `INTERNAL_ERROR`, and the exception is logged once at ERROR
without a stack trace. A `GraphQLError` exception is sent as it is. A `GraphQLExceptionMapper<E>` bean maps an exception
type to an error with a message and extensions (`@Order` or `Ordered` decides between mappers; a failure of a
`CompletionStage` is matched by its real type); a `GraphQLErrorsCustomizer` bean changes the final list of errors;
`jakarta.validation.ConstraintViolationException` becomes one error for each violation with its argument. Two options
(`micronaut.graphql.tools.errors.details` and `micronaut.graphql.tools.errors.stack-traces`, both `false` by default) show
the message in development and add the stack trace to the log entry. A `GraphQLBuilderCustomizer` that sets its own
handler replaces the library's. See the Errors page of the guide.

```java
@Singleton
class OutOfStockMapper implements GraphQLExceptionMapper<OutOfStockException> {
    @Override
    public GraphQLError map(OutOfStockException exception, GraphQLExceptionContext context) {
        return context.error(exception.getMessage(), Map.of("code", "OUT_OF_STOCK"));
    }
}
```

## Building the GraphQL bean yourself

The library creates two beans: the bound `GraphQLSchema`, and the `GraphQL` engine that runs it. Each one is created
only when the application has no bean of that type:

- An application that configures the engine itself (instrumentation, execution strategies, its own transport)
  provides a `GraphQL` bean built from the library's `GraphQLSchema` bean. The library then creates no `GraphQL`
  bean, so there is exactly one, and `GraphQLBuilderCustomizer` beans are not called.
- An application that provides its own `GraphQLSchema` bean gets no binding from the library (no
  `TypeDefinitionRegistry` is needed); the library's `GraphQL` bean, unless the application has one too, runs that
  schema.

```java
@Bean
@Singleton
public GraphQL graphQL(GraphQLSchema schema, MyInstrumentation instrumentation) {
    return GraphQL.newGraphQL(schema).instrumentation(instrumentation).build();
}
```

[`test-suite-java/src/test/java/example/host`](test-suite-java/src/test/java/example/host) is such an application: it supplies the
`TypeDefinitionRegistry`, consumes only the library's `GraphQLSchema` and builds its own `GraphQL` with its own
instrumentation, which then serves the HTTP requests.

The library brings `io.micronaut.graphql:micronaut-graphql` as an API dependency, and the library's HTTP endpoint (which
replaces the controller of that dependency) serves whichever `GraphQL` bean exists.

## Examples

Three example applications serve the same schema (the same resolvers, types and data) in each language:
[test-suite-java](test-suite-java), [test-suite-kotlin](test-suite-kotlin) (KSP) and
[test-suite-groovy](test-suite-groovy). Their classes have the same fully qualified names, so a documentation snippet can
include a language's source by path. The guide's quickstart code is in the `docs-examples-*` modules, apart from these suites.

## Compatibility

The library is built and tested against graphql-java by role. The versions of each role live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml):

| Role | graphql-java | graphql-java-extended-scalars | java-dataloader | Where it runs |
|---|---|---|---|---|
| `floor` | 21.5 | 21.0 | 3.2.1 | full build, JDK 25 |
| `default` | what `micronaut-graphql` 4.8.0 manages (22.4), not pinned by this build | 22.0 | as graphql-java brings it | full build on JDK 25 and 21, library check on JDK 17, native tests on GraalVM 25; the published POM |
| `ceiling` | 26.2 | 24.0 | 6.0.0 | full build, JDK 25; native tests on GraalVM 25; the `graphql-java-compat` job (main compiled on `default`, tested on `ceiling`) |
| `latest` | newest release (`latest.release`) | newest release | newest release | the canary only, never a required check |

The graphql-java axis runs on JDK 25; JDK 21 and JDK 17 run the `default` role only. Micronaut 4.10.x and micronaut-graphql 4.8.0
are the base of every role.

**Support policy.** The supported graphql-java lines are 21.x up to the ceiling line. They are tested at the newest patch of
the floor, the default and the ceiling. A new graphql-java major enters through the `latest` canary and becomes the ceiling
through a promotion issue once it is green. graphql-java-extended-scalars has no 25.x or 26.x release; 24.1 links against 26.2.

The published POM does not pin graphql-java: it arrives through `micronaut-graphql` (4.8.0 brings 22.4). Pin
graphql-java in your build to the line you run, and use the matching extended-scalars version from the table.

To build or test against a role locally:

```shell
./gradlew build                              # default
./gradlew build -PgraphqlJavaLeg=floor       # graphql-java 21.5
./gradlew build -PgraphqlJavaLeg=ceiling     # graphql-java 26.2
./gradlew build -PgraphqlJavaLeg=ceiling -PgraphqlJavaCompileLeg=default   # graphql-java-compat: main compiled on default
```

`-PgraphqlJavaVersion` no longer exists: setting it fails the build with a message that names `-PgraphqlJavaLeg`.

## Artifacts and versions

| Artifact | Coordinates |
|---|---|
| Library | `io.micronaut.graphql.tools:micronaut-graphql-tools` |
| Binding rules (internal, pulled in by the library) | `io.micronaut.graphql.tools:micronaut-graphql-tools-binding` |
| Standard scalars ([`java.time`, JSON and extended scalars](src/main/docs/guide/scalars.adoc)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-scalars` |
| Metrics ([Micrometer](#observability)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-micrometer` |
| Tracing ([OpenTelemetry](#observability)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-opentelemetry` |
| File upload ([`Upload` scalar and multipart route](#file-upload)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-upload` |
| Security ([principal, `@Secured` and the root fields hook](#security)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-security` |
| Testing ([in-process tester](#testing)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-testing` |
| Annotation processor ([compile-time checks](#compile-time-checks)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-processor` |
| BOM | `io.micronaut.graphql.tools:micronaut-graphql-tools-bom` |

### Consuming milestone previews

The 1.0 line ends with the release candidate `1.0.0-RC1`. Milestone versions (`1.0.0-M1`, `1.0.0-M2`, ...) are published to
the GitHub Packages Maven registry of this repository: `https://maven.pkg.github.com/donbeave/micronaut-graphql-tools`. They keep the `io.micronaut.graphql.tools`
group, but they are milestone previews from this repository: they are not affiliated with, and not published by, the
Micronaut Foundation, and the group is not on Maven Central until the project is contributed. The versions are listed on the
[packages page](https://github.com/donbeave?tab=packages&repo_name=micronaut-graphql-tools). There are no snapshots and no
GitHub Releases.

GitHub Packages requires authentication to download from its Maven registry, also for public packages. Create a
[personal access token (classic)](https://github.com/settings/tokens) with the `read:packages` scope (the Maven registry
accepts only classic tokens) and hand it to the build:

- Gradle: put `gpr.user=<your GitHub user name>` and `gpr.key=<token>` in `~/.gradle/gradle.properties` (or export
  `GITHUB_ACTOR` and `GITHUB_TOKEN`), and use the `repositories` block of the quick start. Never commit the token.
- Maven: add a server to `~/.m2/settings.xml` whose id is the repository id of your POM (`github-graphql-tools` above):

```xml
<settings>
    <servers>
        <server>
            <id>github-graphql-tools</id>
            <username>YOUR_GITHUB_USER</username>
            <password>YOUR_TOKEN</password>
        </server>
    </servers>
</settings>
```

In GitHub Actions of another repository, store such a token as a secret; that workflow's own `GITHUB_TOKEN` can read the
package only if the package grants the repository access.

Milestone previews carry no compatibility promise: names and behaviour can change between milestones (see the migration
notes). A published version is not replaced; a fix ships in the next milestone.

The version comes from `projectVersion` in `gradle.properties` (`1.0.0-SNAPSHOT` on `master`). To build a fixed
version, override it on the command line; a version without `-SNAPSHOT` is published as a release version:

```shell
# into the local Maven repository (~/.m2/repository, or -Dmaven.repo.local=<dir>)
./gradlew publishToMavenLocal -PprojectVersion=1.0.0-internal.1

# into a Maven repository of your own (Nexus, Artifactory, ...)
./gradlew publishAllPublicationsToExternalRepository -PprojectVersion=1.0.0-internal.1 \
    -Dio.micronaut.publishing.uri=https://repo.example.com/maven-releases \
    -Dio.micronaut.publishing.username=<user> \
    -Dio.micronaut.publishing.password=<password>
```

The `io.micronaut.publishing.*` settings are JVM system properties (`-D`), not Gradle project properties. Publish from
the default graphql-java leg only (no `-PgraphqlJavaLeg` or `-PgraphqlJavaCompileLeg`): the published POM takes its versions from the build's
resolved dependencies, so the build refuses to run a publish task on another leg.

Both commands publish the library and the annotation processor (jar, sources, javadoc, POM and Gradle module
metadata) and the BOM. Consumers then
add the repository and depend on `io.micronaut.graphql.tools:micronaut-graphql-tools:<version>`.

The japicmp check (`findBaseline`/`japiCmp`) is disabled until the first version is released; see the
module conventions in `buildSrc`.

## Why GraphQL Java Tools?

* **Micronaut**:  Leverage the Micronaut framework effectively, ensuring high performance by avoiding reflection, which can slow down applications. Binding uses Micronaut's compile-time bean and introspection metadata.
* **Minimal Boilerplate**:  Skip manual configuration of GraphQL types, resolvers and options by using annotations like `GraphQLType`, `@GraphQLRootResolver`, and others.
* **Schema First**:  Micronaut GraphQL Tools allows you to directly use your schema by [GraphQL schema language](http://graphql.org/learn/schema/) instead of code-driven approach.
* **Class Validation**:  Micronaut GraphQL Tools will warn you if you provide classes/types that you don't need to, as well as erroring if you use the wrong Java class for a certain GraphQL type when it builds the schema.

## Snapshots and Releases

Milestone versions (`1.0.0-M<n>`) are published to the GitHub Packages Maven registry of this repository
by the `Release` workflow (`.github/workflows/release.yml`), after the full build is green on the tagged commit. See
[Consuming milestone previews](#consuming-milestone-previews) to use them. Nothing is published to Maven Central, no
GitHub Release is created and there are no snapshots; that stays so until the project is contributed to the Micronaut
Foundation. CI (`.github/workflows/gradle.yml`) builds and tests the project.

Releases are cut by the maintainers by pushing a version tag, as described in the [Releases](CONTRIBUTING.md#releases)
section of CONTRIBUTING.md. To use unreleased code, build it yourself and publish it to a local or private repository as
described in [Artifacts and versions](#artifacts-and-versions).
