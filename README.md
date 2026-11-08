# Micronaut GraphQL Tools

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.project-template/micronaut-graphql-tools.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.project-template%22%20AND%20a:%22micronaut-graphql-tools%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-graphql-tools/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-graphql-tools/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=micronaut-projects_micronaut-template&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=micronaut-projects_micronaut-template)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

Schema-first approach for building GraphQL applications with Micronaut.

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-graphql-tools/latest/guide/) for more information.

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-graphql-tools/snapshot/guide/) for the current development docs.

## Quick start

1. Add the necessary dependencies in build.gradle or pom.xml (no version is on Maven Central yet, see [Artifacts and versions](#artifacts-and-versions)):

Gradle:

```groovy
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-SNAPSHOT")
```

Maven:

```xml
<dependencies>
    <dependency>
        <groupId>io.micronaut.graphql.tools</groupId>
        <artifactId>micronaut-graphql-tools</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

2. Define GraphQL Schema

Create a GraphQL schema file, `schema.graphqls` to define types, queries, and mutations, for example in `src/main/resources` resources folder.

3. Configuring GraphQL Tools

Define a `TypeDefinitionRegistry` bean to load the GraphQL schema.

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
- `@GraphQLField` Marks a method of a `@GraphQLType` class as a field resolver, so that methods with arguments (or a `DataFetchingEnvironment` parameter) can back a GraphQL field.
- `@GraphQLSchemaCoordinate("Type.field")` Names the field a resolver method binds; meant for resolver contracts (interfaces), see [Resolver contracts](#resolver-contracts).

These are all the annotations the library provides (package `io.micronaut.graphql.tools.annotation`). GraphQL enums map to Java enums, and unions and interfaces to Java interfaces, without an annotation of their own; the concrete classes of a union are registered as shown in [Custom types mapping](#custom-types-mapping).

## Return types

The declared return type of a field (a resolver method or a bean property) may wrap the mapped class in the
containers graphql-java resolves at runtime, outermost first and each at most once:

- `CompletionStage<T>`, for the field itself only;
- `Publisher<T>` (`org.reactivestreams.Publisher`, any subtype such as `Flux`), required on fields of the subscription
  type and allowed nowhere else; the wrappers below then apply to each event;
- `DataFetcherResult<T>`, for the field or each list element; its errors and local context are applied;
- `Optional<T>`, for the field or each list element, and only where the GraphQL type is nullable (an empty
  `Optional` becomes `null`).

The mapping then checks `T`. Anything else fails at startup with `UnsupportedReturnTypeException`. In particular:

- `List<CompletionStage<T>>` is rejected: graphql-java does not wait for futures inside a list, and the client would
  receive the future's `toString()`. Return `CompletionStage<List<T>>` instead.
- `Optional<T>` on a non-null type (`String!`, `[String!]`) is rejected.
- A field of the subscription type must return a `Publisher` (optionally inside a `CompletionStage`); otherwise the
  startup fails (graphql-java would fail only on the first subscription request). A `Publisher` on any other field,
  or inside a list, is rejected.
- `DataFetcherResult<Publisher<T>>` is not supported on a subscription field, although graphql-java would unwrap it:
  return the `Publisher` and, if needed, a `DataFetcherResult` per event.

## Resolver contracts

`@GraphQLRootResolver` and `@GraphQLTypeResolver` are inherited (`@Inherited`). They may sit on an interface, for
example one emitted by a code generator, and a bean that implements it is a resolver without a GraphQL annotation of
its own. Micronaut AOP advice (`@Around`) on the implementation still applies.

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

`@GraphQLSchemaCoordinate` is also inherited and makes the contract methods executable. A method with a coordinate
binds the field its coordinate names and no other: a type resolver method only on that object type (or, for an
interface field, on the implementations of that interface), a root resolver method only on that root operation type.
Methods of beans without coordinates are matched by name. The coordinate is also checked at startup, never trusted:

- On a bean where at least one method carries a coordinate, only those methods bind. Other public methods (helpers)
  are not candidates.
- The startup fails with `InvalidSchemaCoordinateException`, listing every problem, when a coordinate is not of the
  form `Type.field`, names a type or field the schema does not have or a type that is neither an object type nor an
  interface, names a field
  that differs from the method name, names a non-root type on a `@GraphQLRootResolver` or a root operation type on a
  `@GraphQLTypeResolver`, or sits on a bean that is neither.
- It also fails when the method does not end up bound to exactly its coordinate: for example the type is mapped to a
  class that the `@GraphQLTypeResolver` does not resolve, a property of the mapped class binds the field (with
  `precedence: property-first`, see [Property or type resolver](#property-or-type-resolver)). A coordinate whose type
  is unreachable and allowed (see [Configuration](#configuration)) is not bound and is logged as a warning.

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
always did; give it a coordinate to scope it to one. When more than one method can bind one root field (two beans with
a method of that name, two methods with one coordinate, or a coordinate method and a method of a bean without
coordinates), the startup fails with `MultipleMethodsFoundException` naming the field and every method. Before, the
first bean found won, which depended on the order Micronaut returned the beans in. There is no option to restore that.

**Inheritance from classes.** The annotations are inherited from superclasses too, so a bean that extends an annotated
class is a resolver as well: with the annotated class and a `@Singleton` subclass, both are registered. When both
then bind the same field, the startup fails with `MultipleMethodsFoundException` naming both classes. Annotate only the
class that should be the resolver.

**Several contracts on one bean.** A bean may implement type resolver contracts for different classes. The
annotation metadata keeps only one of the inherited values, so the class hierarchy is read to find every contract, and
each method binds for the class of its first parameter (the source object). A public method whose first parameter is
none of those classes fails the startup with `AmbiguousTypeResolverException`, naming the bean and every contract (on a
bean that uses `@GraphQLSchemaCoordinate`, only methods with a coordinate count).

This is the one place where the library reads class annotations by reflection
(`Class.getDeclaredAnnotation(GraphQLTypeResolver.class)` on the bean class, its superclasses and interfaces). The
annotations are retained at runtime, so this works on the JVM. Where class annotations are not available at runtime,
for example in a native image without reflection configuration for the contract types, a bean annotated directly
keeps its annotation value, and a bean that is a type resolver only through an inherited annotation fails the startup
with `TypeResolverContractsNotFoundException`. Annotate such a bean directly or make the annotations of its contract
types available to reflection.

## Property or type resolver

A field of an object type is bound by a member of its Java type (a property, or a `@GraphQLField` method) or by a
type resolver method (of a `@GraphQLTypeResolver` for that type, or for the Java interface of an SDL interface that
declares the field). When both exist for one field, `micronaut.graphql.tools.precedence` decides:

- `strict` (default): the startup fails with `AmbiguousBindingException`, naming the field and both members.
- `property-first`: the member of the Java type binds, and the type resolver method is never called. This was the
  only behaviour before the option existed.
- `resolver-first`: the type resolver method binds, and the member is not read.

A field may say which one it wants: list directives in `micronaut.graphql.tools.resolver-directives`, and a field
that carries one of them is bound by its type resolver method whatever the precedence. Such a field fails the startup
with `ResolverRequiredException` when only a member of the Java type could bind it. Every listed directive must be
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

With the `strict` default, a getter that happens to have the name of a field a type resolver computes no longer
shadows the type resolver silently. An application that relied on the getter winning sets `precedence: property-first`
(and gets a warning that lists the fields where it applies). A field without both members binds as before in every
mode.

## Startup errors

The schema is bound when the `GraphQL` bean is created. Every field that cannot be bound is reported; the binding does
not stop at the first one. A single failure is thrown as its own exception (`MethodNotFoundException`,
`MultipleMethodsFoundException`, `IncorrectClassMappingException`, ...). Several are thrown together as
`BindingFailuresException`, sorted by schema coordinate: `getFailures()` lists them, the first is the cause and the
others are suppressed exceptions. The checks that need a complete binding (unreachable types, schema coordinates) run
only when no field failed.

Each failure names the schema coordinate (`Type.field`, `Type.field(argument:)` or `InputType.field`) and where it is
declared, as graphql-java parsed it (the source name appears when the schema was parsed with one, for example through
`MultiSourceReader`), followed by the candidates that were considered:

```
The method `missing` not found in any root resolvers: [example.BookQuery].
  GraphQL coordinate: Query.missing (books.graphqls, line 9, column 3)
  GraphQL object type: Query
  GraphQL field: missing
```

`getMappingContext().getCoordinate()` and `getMappingContext().getSourceLocation()` give the same information to code.

## Configuration

| Property | Values (default first) | Meaning |
|---|---|---|
| `micronaut.graphql.tools.unreachable-types` | `fail` \| `ignore` | Object, interface and union types of the schema that no field reachable from a root operation type returns have no binding. `fail` stops the startup with one error that lists all of them; `ignore` keeps them in the schema with graphql-java's default data fetchers and logs them as a warning. |
| `micronaut.graphql.tools.allow-unreachable` | list of type names (empty) | Unreachable types that are allowed even with `fail`; they are logged at INFO. Every entry must be an unreachable object, interface or union type of the schema, otherwise the startup fails, so the list cannot go stale. |
| `micronaut.graphql.tools.precedence` | `strict` \| `property-first` \| `resolver-first` | What binds a field that both a member of its Java type and a type resolver method can bind, see [Property or type resolver](#property-or-type-resolver). `strict` fails the startup. |
| `micronaut.graphql.tools.resolver-directives` | list of directive names (empty) | A field carrying one of these directives is bound by a type resolver method, and fails the startup if none binds it. Each must be declared in the schema. |

```yaml
micronaut:
  graphql:
    tools:
      allow-unreachable:
        - LegacyReport
```

## Custom types mapping

GraphQL interfaces and union types often have multiple concrete implementations.

Define a `SchemaMappingDictionaryCustomizer` bean to register custom types if any.

In the example below, the `SchemaMappingDictionaryCustomizer` bean is used to register the custom implementations of the `Error` interface type. This associates the GraphQL types `SecurityError` and `ValidationError` with their respective Java implementations (`SecurityError.class` and `ValidationError.class`).

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
  with `AmbiguousTypeMappingException`, which names both GraphQL types and both Java types: an object of that class
  would match both.
- A class and its subclass may be mapped to two GraphQL types. A union or interface resolves an object of either class by
  its exact class; an object of a further subclass is an instance of both and fails as described below.
- Two fields that declare different Java types for one GraphQL type fail with `MappingConflictException`, unless the
  two types are an interface and a class annotated with `@GraphQLType(TheInterface.class)` that is read through the
  same bean introspection.

### Unions and interfaces

- A field that returns a GraphQL union or interface declares a Java interface (for example `List<Section>` for
  `[Section!]!`); a class fails the startup.
- Every member of the union and every object type that implements the interface needs its class registered as above,
  otherwise the startup fails (`UnionTypeMappingNotProvidedException`, `InterfaceTypeMappingNotProvidedException`).
  Each of these object types is bound like any other, so every field of the interface must be bound on every
  implementation. That an object type declares all fields of its interfaces is checked by graphql-java.
- Each of these object types must be mapped to a subtype of the Java interface the field declares (the class or
  interface implements or extends it), otherwise the startup fails with `IncorrectMemberMappingException`: no object
  the field returns could resolve to that object type.
- At runtime the object type of a returned object is chosen in two steps, and the result is cached per class:
  1. **Exact class:** the class an object type is mapped to, or, for an object type mapped to a Java interface, a class
     annotated with `@GraphQLType(TheInterface.class)`.
  2. **Assignability:** otherwise, the one object type whose Java type the object is an instance of: a subclass of a
     mapped class (including runtime subclasses such as proxies) or any implementation of a mapped Java interface.

  An object that matches no object type, or more than one in the second step, is a GraphQL error on that field (the
  data of the field is `null`) that names the union or interface, the path, the class and the candidates; the library
  never picks one:

  ```
  Can't resolve '/book/sections[1]'. Abstract type 'Section' must resolve to an Object type at runtime for field ...
  The object of class com.example.UnknownSection is none of the object types of interface Section:
  MediaSection (com.example.MediaSection), TextSection (com.example.TextSection).

  Can't resolve '/bundle'. Abstract type 'Item' must resolve to an Object type at runtime for field 'Query.item'.
  The object of class com.example.BundleModel is an instance of more than one object type of union Item:
  Digital (com.example.DigitalGraph), Printed (com.example.PrintedGraph). Map its class to one of them, or let it
  implement only one of their Java types.
  ```

  What can be checked at startup is: the dictionary rules above (an interface and its implementation mapped to two
  object types) and the member types against the declared Java interface. Whether one object is an instance of two
  unrelated mapped Java interfaces, or of a subclass of two mapped classes, depends on the runtime class and is
  reported at runtime.
- Interfaces may implement interfaces. An interface that no field returns but that a bound object type implements
  (for example a common interface of the members of an error union) is bound too, and is not an unreachable type.
- A `@GraphQLTypeResolver` for the Java interface that an SDL interface is mapped to binds the fields of that interface
  on every implementing object type that does not bind them itself (the first parameter is the Java interface). Its
  coordinate, if any, is the interface field (`Section.summary`). When an implementation binds such a field itself as
  well (a property, a `@GraphQLField` method or its own type resolver), or two interfaces of it have a resolver for the
  field, the startup fails with `MultipleMethodsFoundException` naming the object type, the field and both members.
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
  the interface's methods without an introspection). No such class fails with `ImplementationNotFoundException` and
  several with `MultipleImplementationsFoundException`; annotate the interface with `@GraphQLType` to read all of them
  and to make implementation-only getters non-candidates.

A getter that only an implementation class declares is not a property of an interface that has its own
introspection. A GraphQL field that only such a getter could back fails at startup with `MethodNotFoundException`,
which names the GraphQL type, the field and the interface:

```
The property or method `draftNote` not found in example.ArticleGraph's type. Properties are read from the interface
example.ArticleGraph, so a getter that only an implementation class declares is not a property.
  GraphQL object type: Article
  GraphQL field: draftNote
```

Declare the getter on the interface, or resolve the field with a `@GraphQLTypeResolver(TheInterface.class)` method.
**Behaviour change:** before, such an interface was read through its one annotated implementation class, so getters
of that class bound even when the interface did not declare them. There is no option to keep that: the binding
silently depended on which class happened to implement the interface.

## Examples

Examples can be found in the [test-suite](test-suite) directory.

## Compatibility

The library is built and tested on Java 21 against two graphql-java lines (CI runs both):

| graphql-java | graphql-java-extended-scalars | java-dataloader | Micronaut / micronaut-graphql | Java |
|---|---|---|---|---|
| 21.3 (default build) | 21.0 | 3.2.1 | 4.10.x / 4.8.0 | 21 |
| 25.1 | 24.0 | 6.0.0 | 4.10.x / 4.8.0 | 21 |

The published POM does not pin graphql-java: it arrives through `micronaut-graphql` (4.8.0 brings 22.4). Pin
graphql-java in your build to the line you run, and use the matching extended-scalars version from the table
(extended-scalars has no 25.x release; 24.0 works with graphql-java 25.1).

To build or test against a leg locally:

```shell
./gradlew build                            # graphql-java 21.3
./gradlew build -PgraphqlJavaVersion=25.1  # graphql-java 25.1
```

## Artifacts and versions

| Artifact | Coordinates |
|---|---|
| Library | `io.micronaut.graphql.tools:micronaut-graphql-tools` |
| BOM | `io.micronaut.graphql.tools:micronaut-graphql-tools-bom` |

The version comes from `projectVersion` in `gradle.properties` (`1.0.0-SNAPSHOT` on `master`). To build a fixed
version, override it on the command line; a version without `-SNAPSHOT` is published as a release version:

```shell
# into the local Maven repository (~/.m2/repository, or -Dmaven.repo.local=<dir>)
./gradlew publishToMavenLocal -PprojectVersion=1.0.0-internal.1

# into a Maven repository of your own (Nexus, Artifactory, GitHub Packages, ...)
./gradlew publishAllPublicationsToExternalRepository -PprojectVersion=1.0.0-internal.1 \
    -Dio.micronaut.publishing.uri=https://repo.example.com/maven-releases \
    -Dio.micronaut.publishing.username=<user> \
    -Dio.micronaut.publishing.password=<password>
```

The `io.micronaut.publishing.*` settings are JVM system properties (`-D`), not Gradle project properties. Publish from
the default graphql-java leg only (no `-PgraphqlJavaVersion`): the published POM takes its versions from the build's
resolved dependencies, so the build refuses to run a publish task on another leg.

Both commands publish the library (jar, sources, javadoc, POM and Gradle module metadata) and the BOM. Consumers then
add the repository and depend on `io.micronaut.graphql.tools:micronaut-graphql-tools:<version>`.

The binary-compatibility check (`findBaseline`/`japiCmp`) is disabled until the first version is released; see the
`binaryCompatibility` block in the build files.

## Why GraphQL Java Tools?

* **Micronaut**:  Leverage the Micronaut framework effectively, ensuring high performance by avoiding reflection, which can slow down applications. Binding uses Micronaut's compile-time bean and introspection metadata; the one reflective read of class annotations is described in [Resolver contracts](#resolver-contracts).
* **Minimal Boilerplate**:  Skip manual configuration of GraphQL types, resolvers and options by using annotations like `GraphQLType`, `@GraphQLRootResolver`, and others.
* **Schema First**:  Micronaut GraphQL Tools allows you to directly use your schema by [GraphQL schema language](http://graphql.org/learn/schema/) instead of code-driven approach.
* **Class Validation**:  Micronaut GraphQL Tools will warn you if you provide classes/types that you don't need to, as well as erroring if you use the wrong Java class for a certain GraphQL type when it builds the schema.

## Snapshots and Releases

Snapshots are not published automatically at the moment. The CI workflow (`gradle.yml`) builds and tests only; the
snapshot workflow (`publish-snapshot.yml`) is started manually and still runs on Java 11, so it cannot build this
project until it moves to Java 21. Build a version yourself as described in
[Artifacts and versions](#artifacts-and-versions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-graphql-tools/actions).

Releases are completely automated. To perform a release use the following steps:

* [Publish the draft release](https://github.com/micronaut-projects/micronaut-graphql-tools/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3ARelease) to check it passed successfully.
* If everything went fine, [publish to Maven Central](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3A"Maven+Central+Sync").
* Celebrate!
