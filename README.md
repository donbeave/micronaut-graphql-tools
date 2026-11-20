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
| [`graphql-tools-processor`](graphql-tools-processor/README.md) | `:micronaut-graphql-tools-processor` | The annotation processor that runs the compile-time checks. |
| [`graphql-tools-bom`](graphql-tools-bom/README.md) | `:micronaut-graphql-tools-bom` | The bill of materials for the published artifacts. |
| [`graphql-tools-tck`](graphql-tools-tck/README.md) | `:graphql-tools-tck` | The execution-behaviour scenarios that the example suites run. |
| [`benchmarks`](benchmarks/README.md) | `:benchmarks` | JMH benchmarks and the allocation gate. |
| [`test-suite-java`](test-suite-java/README.md) | `:test-suite-java` | The example application in Java, with its tests. |
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
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-M3")
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
        <version>1.0.0-M3</version>
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
- `@GraphQLField` Marks a method of a `@GraphQLType` class as a field resolver, so that methods with arguments (or a `DataFetchingEnvironment` parameter) can back a GraphQL field. `@GraphQLField("name")` on a method or a property binds the field of that name, see [Binding a field to a differently named member](#binding-a-field-to-a-differently-named-member).
- `@GraphQLIgnore` Marks a method, a property or an enum constant that binds no field, so that strict mode does not report it, see [Escape hatches](#escape-hatches).
- `@GraphQLSchemaCoordinate("Type.field")` Names the field a resolver method binds; meant for resolver contracts (interfaces), see [Resolver contracts](#resolver-contracts).

These are all the annotations the library provides (package `io.micronaut.graphql.tools.annotation`). GraphQL enums map to Java enums (with `@Introspected`, see [Enums](#enums)), and unions and interfaces to Java interfaces, without an annotation of their own; the concrete classes of a union are registered as shown in [Custom types mapping](#custom-types-mapping).

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
  `TypeDefinitionRegistryCustomizer` still runs on top of the schema, with either source.
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
its own. The implementing class still needs a bean annotation such as `@Singleton`: the contract's annotation does not
make the class a bean, and the compile-time checks report a contract that no bean class implements. Every bean that
implements the contract is a resolver, so a hand-written fake or delegating adapter that implements it and is a bean is
registered too. Micronaut AOP advice (`@Around`) on the implementation still applies.

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
  [Binding a field to a differently named member](#binding-a-field-to-a-differently-named-member).
- It also fails when the method does not end up bound to exactly its coordinate: for example the type is mapped to a
  class that the `@GraphQLTypeResolver` does not resolve (a method that declares its coordinate wins over a property of
  the mapped class, see [Property or type resolver](#property-or-type-resolver)). A coordinate whose type
  is unreachable and allowed (see [Configuration](#configuration)) is not bound; with `unbound-resolver-method` at `fail` (the default)
  it is an error, otherwise it is logged as a warning.

**Root operation types.** Root resolver methods are scoped per root operation type (`Query`, `Mutation`,
`Subscription`). A method with `@GraphQLSchemaCoordinate("Subscription.status")` binds only that field, so the same
field name on two operation types binds two different methods:

```java
@GraphQLRootResolver
public class StatusQuery {
    @GraphQLSchemaCoordinate("Query.status")
    public String status(String id) { ... }
}

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

## Property or type resolver

A field of an object type is bound by a member of its Java type (a property, or a `@GraphQLField` method) or by a
type resolver method (of a `@GraphQLTypeResolver` for that type, or for the Java interface of an SDL interface that
declares the field). One rule covers object and interface fields. When both exist for one field,
`micronaut.graphql.tools.precedence` decides:

- `fail` (default): the startup fails with `[precedence-ambiguity]` (match on `SchemaBindingException#code()`), naming the field
  and both members. `strict` does not relax it. The value `strict`, the former name of this default, is rejected with
  `[removed-configuration-key]` (BC-29).
- `property-first`: the member of the Java type binds, and the type resolver method is never called. This was the
  only behaviour before the option existed.
- `resolver-first`: the type resolver method binds, and the member is not read. This is the migration setting for
  applications that come from graphql-java-tools or graphql-java-kickstart, where the resolver wins.

Under `property-first` and `resolver-first` every ambiguity is logged at WARN and written to the binding manifest as a
`!precedence-ambiguity` line.

A field may say which one it wants, and then the setting is not consulted and nothing is logged:

- list directives in `micronaut.graphql.tools.resolver-directives`: a field that carries one of them is bound by its
  type resolver method whatever the precedence;
- declare the coordinate on the type resolver method with `@GraphQLSchemaCoordinate("Item.price")`, or the coordinate
  of the interface (`@GraphQLSchemaCoordinate("Node.id")`): the method binds the field whatever the precedence. This
  is how a Relay-style `Node.id` resolver sits next to an entity that has `getId()`;
- annotate the member with `@GraphQLIgnore`: it is no member, so the type resolver method binds the field.

The error message of an ambiguity names every one of these and says that `strict` does not change it.

A field with a resolver directive fails the startup with `[resolver-required]` when only a member of the Java
type could bind it. Every listed directive must be
declared in the schema. The directive is read from the field of the object type: graphql-java does not carry
directives of an SDL interface field over to the implementations, so a directive written only on the interface's
field has no effect here (the field then follows the precedence).

```graphql
directive @computed on FIELD_DEFINITION

type Item {
  name: String!
  price: Long! @computed
}
```

```yaml
micronaut:
  graphql:
    tools:
      resolver-directives:
        - computed
```

With the `fail` default, a getter that happens to have the name of a field a type resolver computes no longer
shadows the type resolver silently. An application that relied on the getter winning sets `precedence: property-first`
(and gets a warning that lists the fields where it applies). A field without both members binds as before in every
mode.

## Binding a field to a differently named member

A member binds the field of its own name unless it says otherwise. Three forms bind a field to a member with another
name: a field named like a Java keyword or an `Object` method (`class`, `default`, `new`, `hashCode`) cannot be bound any
other way, and a migration often meets resolvers named `getAuthor(Book)` for the field `author`.

**A member of the Java type: `@GraphQLField("name")`.** On a method of a `@GraphQLType` class, the method binds the field
of that name. On a property (its field, its getter, or a Kotlin or Groovy property) the property binds it. The value is
optional: `@GraphQLField` alone keeps the name of the method.

```java
// type Parcel { class: String!  headline: String! }
@GraphQLType
public class Parcel {
    @GraphQLField("class")
    public String kind() { ... }

    @GraphQLField("headline")
    private String title;
}
```

A renamed member binds the field of its new name only; a field of its old name needs another member. When two members
bind one field (a renamed one and another, or two renamed to the same field), the startup fails with
`multiple-methods-found` (`MultipleMethodsFoundException`) naming the field coordinate and the members. A member marked
`@GraphQLIgnore` binds nothing. On a resolver bean the value is not read: use the coordinate.

**A resolver method: `@GraphQLSchemaCoordinate("Type.field")`.** The coordinate names the field, whatever the name of
the method is (before, the startup failed when they differed):

```java
@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {
    @GraphQLSchemaCoordinate("Parcel.class")
    public String kind(Parcel parcel) { ... }
}
```

The rest of [Resolver contracts](#resolver-contracts) holds: on a bean where one method declares a coordinate, only the
methods that do bind, and two methods that declare one coordinate fail with `multiple-methods-found`.

**Accessor prefixes: `micronaut.graphql.tools.resolver-accessor-prefixes`.** With `true` (default `false`) a resolver
method of a bean without coordinates named `getX` or `isX` (an upper-case letter after the prefix) also binds the field
`x`, besides the field named like the method. That lets resolvers written for other tools (`getAuthor(Book)`) bind
without renaming. `getAuthor` and `author` in one class both bind `author` and fail the startup. Off, `getAuthor`
binds the field `getAuthor` only. The compile-time checks read `micronaut.graphql.tools.processor.resolverAccessorPrefixes`: set it in the build when the
runtime key is set, so that the checks judge the arguments and the return type of those methods. Without it they leave
a method that only a prefix could bind unreported.

All three are decided once at startup (and by the compile-time checks); a request does no name lookup.

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
(`[field-not-bound] The method ...`). `SchemaBindingException#code()` returns the same code as a `BindingCode`, the one
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
and diffed. Recording is opt-in: set `micronaut.graphql.tools.manifest.record: true` to record it while the schema is
bound (without it nothing is recorded and getting the bean fails with a message saying so). Getting the bean binds the
schema first. With `micronaut.graphql.tools.manifest.enabled: true` it is written
at startup to `micronaut.graphql.tools.manifest.path` (directories are created). Without a path, one line is logged at INFO, and the manifest lines at DEBUG on the `BindingManifestWriter` logger (the manifest is rendered only when DEBUG is on). The format is specified in the guide page "Binding Manifest" (`src/main/docs/guide/manifest.adoc`): the grammar,
the line kinds, the sorting, the escape of non-ASCII characters and the schema hash, which does not depend on the order
of the schema files or of the extensions.

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
  [Java interfaces as object types](#java-interfaces-as-object-types)). Annotate the interface with `@GraphQLType` or
  `@Introspected`.
- **`unmapped-input-property`** (`!unmapped-input-property InputClass#property`): a property the class can set
  (through a setter or the constructor) that no request can ever set. They are reported in one
  `[unmapped-input-property]` (see [Input classes and input types](#input-classes-and-input-types)).
- **`nullable-primitive`** (`!nullable-primitive Query.field(argument:)`): an argument or input class property declared
  as a primitive (`int`, `boolean`, `double`, ...) for a nullable built-in scalar (`Int`, `Boolean`, `Float`,
  `Long`, ...): a `null` (omitted or explicit) cannot reach it, so it arrives as the primitive's default value or fails
  the request. Use the wrapper class or make the GraphQL type non-null. For a custom scalar this fails in every mode.
  A primitive for a non-null type and a primitive return type are fine.
- **`unwired-directive`** (`!unwired-directive name`, one line per directive): a directive the schema applies that no
  `SchemaDirective` bean, `RuntimeWiringCustomizer` or `resolver-directives` entry implements and that
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
`manifest.enabled: true`, start it, then fix, annotate (`@GraphQLIgnore`) or allow each `!` line of the manifest, and
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

## Custom scalars

GraphQL's own scalars (`String`, `Boolean`, `Int`, `Float`, `ID`) and the extended scalars `Long`, `Short`,
`BigDecimal` and `BigInteger` are built in. Any other scalar of the schema needs a `GraphQLScalarMapping` bean: the
graphql-java `GraphQLScalarType` that implements it, and the Java classes that stand for it. A bare `GraphQLScalarType`
bean is not registered; wrap it in a mapping.

```java
@Factory
public class ScalarsFactory {

    @Bean
    @Singleton
    GraphQLScalarMapping localDate() {
        // same Java classes for fields, arguments and input fields
        return GraphQLScalarMapping.of(myLocalDateScalarType, LocalDate.class);
    }

    @Bean
    @Singleton
    GraphQLScalarMapping json() {
        // different classes for output and input
        return GraphQLScalarMapping.builder(ExtendedScalars.Json)
                .outputClasses(Object.class)
                .inputClasses(Object.class, Map.class)
                .build();
    }

}
```

- **Output classes** are the classes the scalar's `serialize` accepts. A field of the scalar type may be backed by one
  of them or a subclass.
- **Input classes** are the classes an argument or an input object field of the scalar type may be declared as, exactly
  one of them. They are normally the classes that `parseValue`/`parseLiteral` produce; a parsed value that is not an
  instance of the declared class is converted by the Micronaut `ConversionService`.
- A primitive matches its wrapper class. On the input side it is accepted only for a non-null GraphQL type
  (`Count!`), because `null` cannot be passed to it.

The library ships no scalar implementations of its own beyond the built-in ones; graphql-java-extended-scalars and
other libraries provide many. A mapping only takes effect for scalars the schema declares.

The startup fails:

- with `[scalar-mapping-not-provided]` when a field, argument or input field uses a scalar that has no mapping;
  the message names the coordinate;
- with `[incorrect-class-mapping]` when the Java type is not one of the mapping's classes, when the mapping
  declares no classes for that side, or when a primitive stands for a nullable input;
- with `[invalid-scalar-mapping]` when two mappings have the same scalar name, or a mapping names a built-in
  scalar.

Values of custom scalars inside input objects reach the input class as the instances the scalar parsed. When the
declared class is another one of the mapping's input classes, the value goes through the Micronaut `ConversionService`,
which has the application's `TypeConverter` beans (see [Converting input values](#converting-input-values)).

## Arguments and input objects

### Binding by name

A resolver method parameter is bound to the field argument **with the same name**, not to the argument at the same
position. The source object (first parameter of a type resolver method) and a trailing `DataFetchingEnvironment`
keep their places; every other parameter is matched by name. Parameters may be declared in any order. The name-to-
position plan is built once at startup, so a request does no name lookup.

```java
// type Query { page(first: Int!, offset: Int!): String! }
String page(Integer offset, Integer first) { ... }   // page(first: 10, offset: 200) -> first=10, offset=200
```

Names come from Micronaut's compile-time metadata (`Argument.getName()`), not from reflection. A method declared in a
class that Micronaut processes has the name of the source, so the compiler does not need `-parameters`. A method
inherited from a class file has the name that the class file carries (`-parameters`, or, for a method with a body, `-g`, which Gradle and Maven pass
by default; an abstract or interface method needs `-parameters`); a class file built with neither has `arg0`, and the failure says to compile it with `-parameters` or to use
`@GraphQLArgument`. If a parameter name cannot match the argument name (for example `long` or `short`),
bind it explicitly:

```java
String size(@GraphQLArgument("long") Integer length, @GraphQLArgument("short") Integer width) { ... }
```

The annotation on a parameter of an interface or superclass method also applies to the overriding method.

The startup fails with the schema coordinate and the parameter when a parameter has no argument of that name
(`[argument-mismatch]`), when two parameters bind to one argument, and when the numbers of parameters and arguments differ. The compile-time check (the visitor of `graphql-tools-processor`)
applies the same rule and reports the same code, `argument-mismatch`, and coordinate. There is no positional opt-out.

**Migration (breaking):** positional matching has ended. A resolver whose parameter names differ from the SDL argument
names now fails the startup. Rename the parameters, or add `@GraphQLArgument("<argument name>")`.

### Converting input values

graphql-java hands a data fetcher the coerced argument values: scalar values, enum constants, a `Map` per input object
and a `List` per list. The library converts each value into the declared Java type of the resolver parameter,
including its type arguments (`List<SomeInput>`, `List<List<SomeInput>>`, `Set<SomeEnum>`). Every converter is built
once at startup, from the declared type and the GraphQL type, and a request only runs it. There is no reflection on the
request path:

- a `String`, `Boolean`, `Integer` (also for an `int` parameter), `Long`, `Short`, `BigDecimal`, `BigInteger` or `Double`
  value is the declared type already and is passed on as it is; a `Float` argument for a `float` parameter or property
  is narrowed from graphql-java's `Double`;
- an enum is the Java constant already, from the values provider of the enum (see [Enums](#enums));
- a list becomes a new, mutable `ArrayList` for `List`, `Collection` and `Iterable`, a `LinkedHashSet` for `Set` (it
  keeps the order of the list), a `TreeSet` for `SortedSet`, or a `LinkedList` for `Deque` and `Queue`; any other declared collection
  fails the startup with `[unsupported-input-type]`;
- an input object becomes an instance of its input class, built from the class's bean introspection
  (`BeanIntrospection`, generated at compile time because `@GraphQLInput` is `@Introspected`): the introspected
  constructor takes the fields that match its parameters (a record, a Kotlin data class or an immutable class), then
  each remaining field is set through its property (`BeanProperty`). A field the client leaves out is not set, so the
  class keeps its own default; an explicit `null` is set to `null`, and left out for a primitive property, which keeps its
  default. A constructor parameter that no field provides gets `null`, or the default of a primitive;
- a custom scalar value that is an instance of the declared input class is passed on; any other value goes through the
  Micronaut `ConversionService`.

Customization goes through Micronaut `TypeConverter` beans and the [scalar mapping](#custom-scalars), not through a
mapper. A `TypeConverter<Map, SomeInput>` bean builds the instances of `SomeInput` itself (the class still needs its
introspection, because the startup checks below read it, and the bean owns the whole instance, including its
`ArgumentValue` properties: the library does not touch the result); a `TypeConverter` for another pair converts the values of a
custom scalar that its scalar parsed to another class.

An input field that the input class cannot take fails the startup (see [Input classes and input types](#input-classes-and-input-types)).

A value that cannot be converted at request time (a custom scalar value that no class of the mapping or the
`ConversionService` accepts, such as a list where the parameter is a `Map`, or an input class whose constructor or setter
throws) is a GraphQL error of the field: `extensions.code` is `INPUT_CONVERSION_FAILED`, `extensions.coordinate` names the
argument as `Type.field(argument)`, and `extensions.inputPath` the input fields and list indexes down to the value. The
message is worded in GraphQL terms, with no Java class name and no stack detail; the cause is logged at debug level. The
field is `null` and the other fields still run.

### Input classes and input types

Input classes are checked against their GraphQL input types at startup, in both directions:

- Every field of an input type needs a settable property of the same name: a setter, a field or a constructor
  parameter. A missing property fails the startup with `[field-not-bound]`; a read-only property that no
  constructor parameter of the same name takes fails with `[input-field-not-settable]`, which names the field as
  `InputType.field`. Such a field would otherwise fail every request that sends it, or be dropped silently.
- A property the class can set (through a setter or the constructor) that the input type does not define fails the
  startup (the check `unmapped-input-property`, see [strict mode](#strict-mode)), all of them in one
  `[unmapped-input-property]`, because no request can set it. Each one is also an
  `!unmapped-input-property InputClass#property` line of the binding manifest.
  `micronaut.graphql.tools.checks.unmapped-input-property` set to `warn` or `ignore` accepts such properties. Read-only
  properties, such as computed getters, are not checked.
  A class that several input types map to is checked once: a property is unmapped only when none of those input types has a
  field for it, so one class may serve a create and a rename input. `@GraphQLIgnore` excludes a server-populated property.

Both checks apply wherever the input type is used: an argument, a list, another input type, an `ArgumentValue`.

### Enums

A GraphQL enum maps to a Java enum, which needs a bean introspection (`@Introspected`; a `@Serdeable` enum has one too).
At startup the library reads the constants from the introspection (`EnumBeanIntrospection`, generated at compile time,
never by reflection) and registers one values provider per bound enum, so graphql-java itself coerces an enum argument,
an enum input field or a schema default value to the Java constant and serializes a returned constant by its schema
name. The resolver receives the constant with no conversion call. A Java enum without an introspection fails the startup
with `[class-not-introspected]`, which names the class and `io.micronaut.core.annotation.Introspected`. A schema
value without a constant fails it with `[missing-enum-values]`; a constant without a schema value is reported in
[strict mode](#strict-mode) (`@GraphQLIgnore` suppresses it, see [Escape hatches](#escape-hatches)). A value that the
schema does not define is a request error from graphql-java and never reaches the resolver. The schema name is the Java
constant name; the library has no rename annotation for enum values.

A `RuntimeWiringCustomizer` cannot register its own `enumValues` provider for a bound enum: that replaces the library's
wiring and fails the startup with `[runtime-wiring-override]`.

### Omitted values and `null`

By default an omitted argument and an explicit `null` both reach the resolver as `null` (an input class property the
client leaves out keeps the value the class gives it). Default values of the schema apply in any case, and enum values
arrive as the Java enum constants of the same name.

To tell them apart, declare the resolver parameter or the input class property as `ArgumentValue<T>`; nothing else is
configured, and input types without an `ArgumentValue` pay nothing for it:

```java
String updateProfile(ArgumentValue<String> nickname, ArgumentValue<ProfileInput> profile) {
    if (nickname.isOmitted()) { /* not sent: keep the stored value */ }
    else if (nickname.isNull()) { /* sent as null: clear it */ }
    else { /* nickname.get() */ }
}
```

- `isOmitted()`: not provided and no schema default; `isNull()`: an explicit `null`; `isPresent()`: a value (provided
  or the schema's default).
- A variable the request does not provide counts as omitted.
- `ArgumentValue` is allowed only for a nullable GraphQL type, not inside a list, and an input class property of that
  type needs a setter or a constructor parameter (the library sets it to `ArgumentValue.omitted()` when the client
  leaves it out, and also when the input type does not define it, for example when another input type backed by the
  same class does). Otherwise, and without the option, the startup fails with `[unsupported-input-type]`.
- A record component, a Kotlin data class `val` (a default argument is not used for it) or a constructor parameter of
  the type is filled at construction: `ArgumentValue.omitted()` when the client leaves the field out and the schema
  declares no default, otherwise `ArgumentValue.ofNullable(value)` (the schema default counts as the value). It works the same in nested input classes and in lists of them. A Groovy class
  declares its constructor itself: `@TupleConstructor` and `@Immutable` add theirs after the introspection is built.
- The conversion decides what is omitted from the keys of the `Map` that graphql-java coerced, while it builds the
  instance. It calls no getter and never walks the converted object again: a write-only property is enough.

## Configuration

| Property | Values (default first) | Meaning |
|---|---|---|
| `micronaut.graphql.tools.schema.locations` | list of locations (`classpath:graphql/**/*.graphqls`) | Where the schema files are read from when the application has no `TypeDefinitionRegistry` bean: `classpath:`, `classpath*:` (dependency jars too) or `file:`, then an Ant-style path. See [Schema discovery](#schema-discovery). |
| `micronaut.graphql.tools.precedence` | `fail` \| `property-first` \| `resolver-first` | What binds a field that both a member of its Java type and a type resolver method can bind, see [Property or type resolver](#property-or-type-resolver). `fail` (default) fails the startup; a field that carries a resolver directive, or whose resolver method declares its coordinate, is not ambiguous. |
| `micronaut.graphql.tools.resolver-accessor-prefixes` | `false` \| `true` | Whether a resolver method named `getX` or `isX` also binds the field `x`, see [Binding a field to a differently named member](#binding-a-field-to-a-differently-named-member). |
| `micronaut.graphql.tools.resolver-directives` | list of directive names (empty) | A field carrying one of these directives is bound by a type resolver method, and fails the startup if none binds it. Each must be declared in the schema. |
| `micronaut.graphql.tools.strict` | `true` \| `false` | The shorthand for the severity of every check that has no `checks` entry: `true` leaves every check at `fail`, `false` makes every check `warn` (the findings are logged and listed in the manifest). It does not change `precedence`. See [Strict mode](#strict-mode). |
| `micronaut.graphql.tools.checks` | map of check code to `fail` \| `warn` \| `ignore` (empty) | The severity of single checks, for example `checks.unwired-directive: ignore`: `unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `nullable-primitive`, `unreachable-type`, `unreachable-value-type`, `unused-dictionary-entry`, `interface-read-through-implementation`, `unwired-directive`. An entry wins over `strict`. An unknown code or severity fails the startup. The five compile-time checks (`unbound-resolver-method`, `root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `nullable-primitive`) are not relaxed at compile time. |
| `micronaut.graphql.tools.eager-init` | `true` \| `false` | Bind the schema when the application context starts, so that a broken binding fails the startup (also when the application defines its own `GraphQL` bean) and the first request does no binding work. `false` binds it when the first bean that needs the `GraphQLSchema` is created. |
| `micronaut.graphql.tools.manifest.record` | `false` \| `true` | Record the [binding manifest](#binding-manifest) while the schema is bound, so that the `BindingManifest` bean can be read. Off by default: nothing is recorded. The checks run the same either way. |
| `micronaut.graphql.tools.manifest.enabled` | `false` \| `true` | Write the [binding manifest](#binding-manifest) at startup (this binds the schema at startup). Implies `manifest.record`. |
| `micronaut.graphql.tools.manifest.path` | file path (none) | Where to write it; without a path one line is logged at INFO and the manifest lines at DEBUG. |
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
coordinate. The build setup for Gradle and Maven (Java, Kotlin with KSP or kapt, Groovy), the options, what is checked at compile
time and what only at startup, the support matrix and the incrementality guarantee are on the guide page
[Compile-Time Verification](src/main/docs/guide/compileTime.adoc); its options are on
[Options](src/main/docs/guide/compileTime/options.adoc). The error codes are in the
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

## Custom types mapping

GraphQL interfaces and union types often have multiple concrete implementations.

Define a `SchemaMappingDictionaryCustomizer` bean to register custom types if any. You may define several, for example one
per module: all of them run, in `@Order`, on one dictionary. A GraphQL type that two of them map fails the startup.

In the example below, the `SchemaMappingDictionaryCustomizer` bean is used to register the Java classes of the members of the `Error` union type. This associates the GraphQL types `SecurityError` and `ValidationError` with their respective Java implementations (`SecurityError.class` and `ValidationError.class`).

```java
@Bean
@Singleton
public SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
return schemaMappingDictionary -> schemaMappingDictionary
        .registerType("SecurityError", SecurityError.class)
        .registerType("ValidationError", ValidationError.class);
}
```

A GraphQL type can be mapped to a class or to a Java interface. An interface and the classes annotated with
`@GraphQLType(TheInterface.class)` are one mapping: for a union member or an implementation of an SDL interface that
is mapped to `TheInterface`, an object of any of those classes resolves to that object type, and a field may declare
either the interface or such a class for the same GraphQL type. The rules, each checked at startup:

- A GraphQL type is mapped once, and a Java type to one GraphQL type. Both errors name the GraphQL types and the Java
  types involved.
- Mapping one GraphQL object type to an interface and another one to a class (or interface) implementing it fails
  with `[ambiguous-type-mapping]`, which names both GraphQL types and both Java types: an object of that class
  would match both.
- A class and its subclass may be mapped to two GraphQL types. A union or interface resolves an object of either class by
  its exact class; an object of a further subclass (such as a proxy) resolves to the most specific of them, as described below.
- Two fields that declare different Java types for one GraphQL type fail with `[mapping-conflict]`, unless the
  two types are an interface and a class annotated with `@GraphQLType(TheInterface.class)` that is read through the
  same bean introspection.

### Unions and interfaces

- A field that returns a GraphQL union or interface declares a Java interface (for example `List<Section>` for
  `[Section!]!`); a class fails the startup.
- Every member of the union and every object type that implements the interface needs its class registered as above,
  otherwise the startup fails (`[union-type-mapping-not-provided]`, `[interface-type-mapping-not-provided]`).
  Each of these object types is bound like any other, so every field of the interface must be bound on every
  implementation. That an object type declares all fields of its interfaces is checked by graphql-java.
- Each of these object types must be mapped to a Java type that an object the field returns could be an instance of.
  The startup fails with `[incorrect-member-mapping]` only for a provably disjoint mapping: a `final` class that
  does not implement the declared Java interface, or two classes with no subtype relation. A mapped Java interface
  that does not extend the declared one is accepted, because one class can implement both.
- At runtime the object type of a returned object is chosen in two steps, and the result is cached per class:
  1. **Exact class:** the class an object type is mapped to, or, for an object type mapped to a Java interface, a class
     annotated with `@GraphQLType(TheInterface.class)`.
  2. **Assignability:** otherwise, the object type whose Java type the object is an instance of: a subclass of a
     mapped class (including runtime subclasses such as proxies) or any implementation of a mapped Java interface.
     When the object is an instance of several, the **most specific** one wins: the object type whose Java type is a
     subtype of the Java types of all the others (for `A` mapped to `Note` and `B` mapped to `LongNote extends Note`,
     a proxy of `LongNote` resolves to `B`). The order of the mappings does not matter.

  An object that matches no object type is a GraphQL error on that field (the data of the field is `null`), and so is
  an object whose candidates have no single most specific one (unrelated Java types, such as a class that implements
  two mapped interfaces, or two object types mapped to the same Java type). The error names the union or interface,
  the path, the class and the candidates; the library never guesses between them:

  ```
  Can't resolve '/book/sections[1]'. Abstract type 'Section' must resolve to an Object type at runtime for field ...
  The object of class com.example.UnknownSection is none of the object types of interface Section:
  MediaSection (com.example.MediaSection), TextSection (com.example.TextSection).

  Can't resolve '/bundle'. Abstract type 'Item' must resolve to an Object type at runtime for field 'Query.item'.
  The object of class com.example.BundleModel is an instance of more than one object type of union Item, and none is
  more specific than the others: Digital (com.example.DigitalGraph), Printed (com.example.PrintedGraph). Map its class to one of them, or let it
  implement only one of their Java types.
  ```

  What is checked at startup is: the dictionary rules above (an interface and its implementation mapped to two
  object types), the member types against the declared Java interface (provably disjoint mappings only) and, for every
  concrete class with a bean introspection, the ambiguity of the members. Such a class that is not mapped by its exact
  class, and is an instance of the Java types of several members of a returned union or interface of which none is more
  specific, fails the startup with `[ambiguous-abstract-type]`, naming the class and the members. A class and its
  sub-member (`B` mapped to `LongNote extends Note` mapped to `A`) is not ambiguous. The check runs once, from the
  introspection metadata, which a native image has, and adds nothing per request:

  ```
  Objects of these classes cannot be resolved to one object type of a union or interface, because no single object type
  is the most specific one:
    com.example.BundleModel is an instance of Digital (com.example.DigitalGraph), Printed (com.example.PrintedGraph) of union Item
  Map each class to one of them with @GraphQLType(TheJavaInterface.class), or let it implement only one of their Java types.
  ```

  A class without an introspection, such as a proxy or a class generated at runtime, can only be checked at runtime,
  where it is the GraphQL error above.
- Interfaces may implement interfaces. An interface that no field returns but that a bound object type implements
  (for example a common interface of the members of an error union) is bound too, and is not an unreachable type.
- A `@GraphQLTypeResolver` for the Java interface that an SDL interface is mapped to binds the fields of that interface
  on every implementing object type that does not bind them itself (the first parameter is the Java interface). Its
  coordinate, if any, is the interface field (`Section.summary`). When an implementation binds such a field itself as
  well (a property, a `@GraphQLField` method or its own type resolver), or two interfaces of it have a resolver for the
  field, the startup fails with `[multiple-methods-found]` naming the object type, the field and both members.
  Only interfaces that a field returns have a Java type, so a resolver for an interface that is only implemented is
  not applied.

## Java interfaces as object types

A field may declare a Java interface for a GraphQL object type. Where its properties come from depends on whether the
interface has a bean introspection of its own:

- **The interface is annotated with `@GraphQLType` (or `@Introspected`)**, so that Micronaut generates an
  introspection for it (the annotation processor has to run where the interface is compiled). The properties are the
  getters of the interface, read from an object of any class that implements it. Any number of classes may implement
  it, and they need no annotation; a class annotated with `@GraphQLType(TheInterface.class)` is read through the
  interface as well. A `@GraphQLTypeResolver(TheInterface.class)` resolver receives the object as `TheInterface`.
- **The interface has no introspection of its own.** Exactly one class annotated with `@GraphQLType(TheInterface.class)`
  is read, with all of its getters, including getters that the interface does not declare (the library cannot read
  the interface's methods without an introspection). No such class fails with `[implementation-not-found]` and
  several with `[multiple-implementations-found]`; annotate the interface with `@GraphQLType` to read all of them
  and to make implementation-only getters non-candidates. [Strict mode](#strict-mode), on by default, rejects this
  case; it binds only with `strict: false`.

A getter that only an implementation class declares is not a property of an interface that has its own
introspection. A GraphQL field that only such a getter could back fails at startup with `[field-not-bound]`,
which names the GraphQL type, the field and the interface:

```
The property or method `draftNote` not found in example.ArticleGraph's type. Properties are read from the interface
example.ArticleGraph, so a getter that only an implementation class declares is not a property.
```

Declare the getter on the interface, or resolve the field with a `@GraphQLTypeResolver(TheInterface.class)` method.
**Behaviour change:** before, such an interface was read through its one annotated implementation class, so getters
of that class bound even when the interface did not declare them. There is no option to keep that: the binding
silently depended on which class happened to implement the interface.

## Customizing the schema

Three kinds of beans change what the library builds. Each kind may have any number of beans; they run lowest order
first, by `@Order` on the bean class or factory method, or by `Ordered#getOrder()` if the bean implements `Ordered`.

| Bean type | Receives | Runs |
|---|---|---|
| `TypeDefinitionRegistryCustomizer` | a copy of the application's `TypeDefinitionRegistry` bean (the bean itself stays unchanged) | before the binding: types and fields added here need a binding like any other |
| `RuntimeWiringCustomizer` | the `RuntimeWiring.Builder` that holds the library's wiring | after the binding, before the schema is generated |
| `GraphQLSchemaCustomizer` | the generated `GraphQLSchema`; returns the schema to use | last |

A `RuntimeWiringCustomizer` may only **add** (scalars, directive wirings, field visibility, ...). If it replaces a data
fetcher, default data fetcher, type resolver or scalar that the library registered, or the wiring factory, or puts a
data fetcher or type resolver for a field or type the library wired into a code registry (`builder.codeRegistry(...)`,
which graphql-java lets win), the startup fails with `[runtime-wiring-override]`, which names every replacement
and the customizer that made it. The check runs only when the application has a `RuntimeWiringCustomizer` bean; the
library's own directive wirings are not checked, and without a customizer the startup does no check work. Code registry entries for fields the library does not wire, and enum value
providers, are allowed.
(On graphql-java 25.2 the builder itself already rejects the first replaced default data fetcher, type
resolver or scalar with `StrictModeWiringException`; 21.5 and 22.4 accept them silently. A second data fetcher for a
field the library wired is reported as `[runtime-wiring-override]` on every line. On some graphql-java lines `RuntimeWiring.Builder#type` for a
type the library wired clears that type's default data fetcher; the library puts it back, so calling `type` to add a
field fetcher is not a replacement.) To wrap data fetchers, use a
`SchemaDirective` bean (`named` or `everyElement`); to change the generated schema, use a `GraphQLSchemaCustomizer` (it replaces
`RuntimeWiring.Builder#transformer`, which graphql-java 25 removed). The library does not check what a
`GraphQLSchemaCustomizer` changes.

Which seam to use to change the schema: a `RuntimeWiringCustomizer` to add wiring before the schema is generated, a
`GraphQLSchemaCustomizer` to change the generated schema as part of the library's own bean (recommended), and a
`BeanCreatedEventListener<GraphQLSchema>` when another module has to replace the `GraphQLSchema` bean. The library's
union and interface type resolvers read the schema of the execution (`TypeResolutionEnvironment#getSchema()`), so
they resolve against the replaced schema too, whatever seam produced it and in whatever order the beans run. Do not
keep a reference to the schema the library built; the data fetchers and type resolvers hold none.

```java
@Bean
@Singleton
@Order(10)
public RuntimeWiringCustomizer hideInternalFields() {
    return builder -> builder.fieldVisibility(BlockedFields.newBlock().addPattern("Query.internal.*").build());
}
```

### Fields a customizer serves

A field that only a hand-written `DataFetcher` serves has no Java member, so the library would fail the startup with
`[field-not-bound]`. The customizer declares such a field by **claiming** it in `claimedFields()` and registers the
data fetcher in `customize`:

```java
@Singleton
class ServerVersionCustomizer implements RuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> claimedFields() {
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
  `claimedFields()` as the way out.
- `claimedFields()` is read once, at startup, before any field is bound. Claimed fields have a line in the
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
  startup with `[duplicate-directive-wiring]`, and a `RuntimeWiringCustomizer` that replaces such a wiring fails
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

A `SchemaDirectiveWiring` bean is **not** applied on its own: if neither a `SchemaDirective` bean wraps it nor a
`RuntimeWiringCustomizer` registers it (`RuntimeWiring.Builder#directive` or `#directiveWiring`), the startup fails with
`[unwrapped-directive-wiring]`, which names the bean. The check runs after every customizer. A wiring therefore
never runs on every element just because it is a bean. To keep a wiring as a bean, inject it into the factory method of
its `SchemaDirective`. A singleton is matched by instance; a `@Prototype` bean (a new instance per injection point) by
its class, so it may be wrapped too. Because of that, an unwrapped `@Prototype` bean goes undetected when some wired
instance (of any bean) has the same class; the check then cannot tell the two instances apart.

**Every directive the schema applies must have a wiring**, or the startup fails with `[unwired-directive]`,
which lists every such directive and where the schema applies it. With the check `unwired-directive` at `warn` (or `strict: false`, see [Strict mode](#strict-mode)) the
same list is a warning instead; either way each such directive is an `!unwired-directive` line of the binding
manifest. A directive counts as wired if a `SchemaDirective` bean names it, if a `RuntimeWiringCustomizer` registers a
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
`TypeDefinitionRegistryCustomizer` can add their declarations unless the SDL declares them:

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
public TypeDefinitionRegistryCustomizer validationDirectives() {
    return registry -> registry.merge(DirectiveConstraints.newDirectiveConstraints().build().getDirectivesDeclaration());
}
```

Use extended-validation 21.0 with graphql-java 21.5, 22.0 with 22.4 and 24.0 with 26.2 (21.0 calls a
`GraphQLCodeRegistry` method that graphql-java 22.4 and later removed); the library's tests run all three.

## Configuring the GraphQL bean

`GraphQLBuilderCustomizer` beans receive the `GraphQL.Builder` of the library's `GraphQL` bean (created with the
library's schema) before it is built, lowest order first (`@Order` or `Ordered`): instrumentation, a
`PreparsedDocumentProvider`, execution strategies, the default `DataFetcherExceptionHandler`, and so on. A builder
setter keeps one value, so a later customizer replaces what an earlier one set there; chain instrumentations with
`ChainedInstrumentation`. The library itself never calls `doNotAddDefaultInstrumentations()`, which graphql-java 25
removed.

```java
@Bean
@Singleton
public GraphQLBuilderCustomizer tracing(MyTracingInstrumentation instrumentation) {
    return builder -> builder.instrumentation(instrumentation);
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

The library brings `io.micronaut.graphql:micronaut-graphql` as an API dependency, and its HTTP controller serves
whichever `GraphQL` bean exists. The library's own code does not use it, so an application with its own transport
can exclude that dependency.

## Examples

Three example applications serve the same schema (the same resolvers, types and data) in each language:
[test-suite-java](test-suite-java), [test-suite-kotlin](test-suite-kotlin) (KSP) and
[test-suite-groovy](test-suite-groovy). Their classes have the same fully qualified names, so a documentation snippet can
include a language's source by path.

## Compatibility

The library is built and tested against graphql-java by role. The versions of each role live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml):

| Role | graphql-java | graphql-java-extended-scalars | java-dataloader | Where it runs |
|---|---|---|---|---|
| `floor` | 21.5 | 21.0 | 3.2.1 | full build, JDK 25 |
| `default` | what `micronaut-graphql` 4.8.0 manages (22.4), not pinned by this build | 22.0 | as graphql-java brings it | full build on JDK 25 and 21, library check on JDK 17, native tests on GraalVM 25; the published POM |
| `ceiling` | 26.2 | 24.0 | 6.0.0 | full build, JDK 25; native tests on GraalVM 25; binary compatibility (main compiled on `default`, tested on `ceiling`) |
| `latest` | newest release (`latest.release`) | newest release | newest release | the canary only, never a required check |

The graphql-java axis runs on JDK 25; JDK 21 and JDK 17 run the `default` role only. Micronaut 4.10.x and micronaut-graphql 4.8.0
are the base of every role.

**Support policy.** The supported graphql-java lines are 21.x up to the ceiling line. They are tested at the newest patch of
the floor, the default and the ceiling. A new graphql-java major enters through the `latest` canary and becomes the ceiling
through a promotion issue once it is green. graphql-java-extended-scalars has no 25.x or 26.x release; 24.0 links against 26.2.

The published POM does not pin graphql-java: it arrives through `micronaut-graphql` (4.8.0 brings 22.4). Pin
graphql-java in your build to the line you run, and use the matching extended-scalars version from the table.

To build or test against a role locally:

```shell
./gradlew build                              # default
./gradlew build -PgraphqlJavaLeg=floor       # graphql-java 21.5
./gradlew build -PgraphqlJavaLeg=ceiling     # graphql-java 26.2
./gradlew build -PgraphqlJavaLeg=ceiling -PgraphqlJavaCompileLeg=default   # binary compatibility: main compiled on default
```

`-PgraphqlJavaVersion` no longer exists: setting it fails the build with a message that names `-PgraphqlJavaLeg`.

## Artifacts and versions

| Artifact | Coordinates |
|---|---|
| Library | `io.micronaut.graphql.tools:micronaut-graphql-tools` |
| Binding rules (internal, pulled in by the library) | `io.micronaut.graphql.tools:micronaut-graphql-tools-binding` |
| Annotation processor ([compile-time checks](#compile-time-checks)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-processor` |
| BOM | `io.micronaut.graphql.tools:micronaut-graphql-tools-bom` |

### Consuming milestone previews

Milestone versions (`1.0.0-M1`, `1.0.0-M2`, ...) and, later, `1.0.0` are published to the GitHub Packages Maven registry of
this repository: `https://maven.pkg.github.com/donbeave/micronaut-graphql-tools`. They keep the `io.micronaut.graphql.tools`
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

The binary-compatibility check (`findBaseline`/`japiCmp`) is disabled until the first version is released; see the
`binaryCompatibility` block in the build files.

## Why GraphQL Java Tools?

* **Micronaut**:  Leverage the Micronaut framework effectively, ensuring high performance by avoiding reflection, which can slow down applications. Binding uses Micronaut's compile-time bean and introspection metadata; the one reflective read of class annotations is described in [Resolver contracts](#resolver-contracts).
* **Minimal Boilerplate**:  Skip manual configuration of GraphQL types, resolvers and options by using annotations like `GraphQLType`, `@GraphQLRootResolver`, and others.
* **Schema First**:  Micronaut GraphQL Tools allows you to directly use your schema by [GraphQL schema language](http://graphql.org/learn/schema/) instead of code-driven approach.
* **Class Validation**:  Micronaut GraphQL Tools will warn you if you provide classes/types that you don't need to, as well as erroring if you use the wrong Java class for a certain GraphQL type when it builds the schema.

## Snapshots and Releases

Milestone versions (`1.0.0-M<n>`) and, later, `1.0.0` are published to the GitHub Packages Maven registry of this repository
by the `Release` workflow (`.github/workflows/release.yml`), after the full build is green on the tagged commit. See
[Consuming milestone previews](#consuming-milestone-previews) to use them. Nothing is published to Maven Central, no
GitHub Release is created and there are no snapshots; that stays so until the project is contributed to the Micronaut
Foundation. CI (`.github/workflows/gradle.yml`) builds and tests the project.

Releases are cut by the maintainers by pushing a version tag, as described in the [Releases](CONTRIBUTING.md#releases)
section of CONTRIBUTING.md. To use unreleased code, build it yourself and publish it to a local or private repository as
described in [Artifacts and versions](#artifacts-and-versions).
