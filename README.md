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
  is unreachable and allowed (see [Configuration](#configuration)) is not bound; with `strict: false` that is logged
  as a warning, in [strict mode](#strict-mode) (the default) it is an error.

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
`ping()` for `Query.ping` and `Mutation.ping`), [strict mode](#strict-mode), on by default, fails the startup;
`strict: false` allows it. When more than one method can bind one root field (two beans with
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

## Binding manifest

The `BindingManifest` bean says what the schema is bound to: one line per schema coordinate, naming the Java member
that serves it, in a stable text format (`graphql-binding-manifest 1`) that can be checked into a repository and
diffed. Getting the bean binds the schema first. With `micronaut.graphql.tools.manifest.enabled: true` it is written
at startup to `micronaut.graphql.tools.manifest.path` (directories are created), or logged at INFO when no path is
set.

```
# graphql-binding-manifest 1
# binder: micronaut-graphql-tools 1.0.0
# graphql-java: 21.3
# schema-sha256: 207c5aaec4894a775f03ae20eaa6b97ba4293ed6608936aa65c812af3a469469
!unreachable Legacy
Book → object · example.data.Book · - · -
Book.sellers: [BookSeller!]! → resolver · example.graphql.type.resolver.BookResolver#sellers(example.data.Book, graphql.schema.DataFetchingEnvironment) · java.util.concurrent.CompletionStage<java.util.List<example.data.BookSeller>> · java.util.List<example.data.BookSeller>
Book.title: String! → property · example.data.Book#getTitle() · java.lang.String · java.lang.String
Error → union · example.graphql.type.Error · - · -
Error/SecurityError → resolves · example.graphql.type.SecurityError · - · -
Query.allBooks: [Book!]! → root · example.graphql.query.AllBooksQuery#allBooks() · java.util.List<example.data.Book> · java.util.List<example.data.Book>
SecurityErrorCode.NOT_AUTHENTICATED → enumValue · example.graphql.type.SecurityErrorCode#NOT_AUTHENTICATED · - · -
SignUpInput.username: String! → inputField · example.graphql.input.SignUpInput#username · java.lang.String · java.lang.String
String → scalar · graphql.scalar.GraphqlStringCoercing · - · -
```

- The header names the format, the library version (`unknown` outside a jar), the graphql-java version and the SHA-256
  of the schema definitions (sorted by name, printed by graphql-java's `AstPrinter`).
- Type lines: `object`, `interface`, `union`, `enum`, `input` with their Java type, `scalar` with its `Coercing` class
  (only scalars that a bound field, argument or input field uses). Root operation types have no Java type (`-`).
- Field lines: `Type.field(argument: Type): Type → kind · member · declared type · converted type`. The kind is `root`,
  `resolver` (a type resolver method, also of an interface) or `property` (a getter or a `@GraphQLField` method of the
  Java type). Methods are written `Class#method(parameter types)` with every Java parameter: a root or type resolver
  method on its bean class, also when the bean inherits it (AOP proxies are unwrapped), a `@GraphQLField` method on the
  class it is written in; getters on the class that declares them. Parameter and declared types are those of the
  declaration, so a type variable of a generic superclass stays a type variable (`T`). The converted type is the
  declared type without `CompletionStage`, `Publisher` (subscription fields only), `DataFetcherResult` (also per list
  element) and `Optional`, unwrapped once in that order. Interface fields have no lines of their own: each object type
  that implements them has its line.
- `resolves` lines map each member of a union and each implementation of an interface to its Java type;
  `enumValue` and `inputField` lines list enum values and input fields.
- Lines starting with `!` are findings: `!unreachable Type` for a declared type nothing binds (allowed or ignored, see
  [Configuration](#configuration); an enum, input or scalar type that an argument of a directive definition uses,
  directly or through input fields, is reached), `!ambiguous Type.field · member | member` for a field decided by a lenient
  `precedence`, and the findings of [strict mode](#strict-mode) (`!unbound`, `!unusedDictionary`, `!unwiredDirective`).
- Java types are written as `java.lang.reflect.Type#getTypeName()` writes them, from the reflected method or getter.
  Where reflection cannot find it (for example a native image without reflection configuration), they are written from
  Micronaut's compile-time metadata, which keeps no wildcards (`List<? extends Book>` is written
  `java.util.List<Book>`), no raw types (`List` is written `java.util.List<java.lang.Object>`) and no type arguments of
  array components (`List<String>[]` is written `java.util.List[]`); the first such member is logged at INFO. The body is
  sorted by `String#compareTo`, UTF-8, `LF`; nothing is escaped, and a part that would contain ` → ` or ` · ` fails
  instead.

## Strict mode

Strict mode (`micronaut.graphql.tools.strict`, on by default) fails the startup on bindings that work but are probably
mistakes. Each finding is also a `!` line of the [binding manifest](#binding-manifest), with or without strict mode:

- **Unreachable types of every kind.** Without strict mode, only object, interface and union types that nothing binds
  are checked (`unreachable-types`). Strict mode adds enum, input and scalar types that no bound field, argument or input
  field uses (`!unreachable`); they are reported in the same `UnreachableTypesException`, and `allow-unreachable` may
  name them. `unreachable-types: ignore` ignores them too. A type that an argument of a directive definition uses,
  directly or through input fields, is reached.
- **Resolver methods that bind no field** (`!unbound Class#method(...)`): every executable method of a
  `@GraphQLRootResolver` or `@GraphQLTypeResolver` bean, which Micronaut makes of its public methods, must bind a
  field. On a bean that uses `@GraphQLSchemaCoordinate`, only the annotated methods count, so a contract
  implementation may keep public helpers. Overrides of `Object`'s methods (`toString()`, `equals`, `hashCode()`) do
  not count. A coordinate on a type that is unreachable and allowed is such a method too. A type resolver method that
  `precedence: property-first` leaves unbound is accounted for by the `!ambiguous` line instead.
- **One root method for several operation types.** A root resolver method without a coordinate that binds the field
  of its name on more than one root operation type (`Query.ping` and `Mutation.ping`). Give each operation type its
  own method with `@GraphQLSchemaCoordinate`. This finding has no `!` line of its own in the manifest (its field lines
  show the shared method).
- **Java enum constants without a schema value** (`!unbound Enum#CONSTANT`): such a constant cannot be returned
  (graphql-java fails to serialize it) and never arrives as an argument.
- **Dictionary entries for types that are not bound** (`!unusedDictionary Type = Class`), except for allowed
  unreachable types.
- **Java interfaces read through an implementation.** An object type mapped to a Java interface that has no bean
  introspection of its own is read through the introspection of its one `@GraphQLType(TheInterface.class)` class, so
  a getter only the implementation declares binds silently (see [Java interfaces as object types](#java-interfaces-as-object-types)).
  Annotate the interface with `@GraphQLType` or `@Introspected`. This finding has no `!` line of its own in the
  manifest (format version 1 has no kind for it).
- **Input class properties that the input type does not define** (`!unbound InputClass#property`): a property the
  class can set (through a setter or the constructor) that no request can ever set. They are reported in one
  `UnmappedInputPropertiesException`. `input.unmapped-properties` decides on its own when it is set: `fail` fails
  without strict mode too, `ignore` accepts them in strict mode (see
  [Input classes and input types](#input-classes-and-input-types)).
- **A primitive for a nullable built-in scalar.** An argument or input class property declared as a primitive (`int`,
  `boolean`, `double`, ...) for a nullable built-in scalar (`Int`, `Boolean`, `Float`, `Long`, ...): a `null` (omitted
  or explicit) cannot reach it, so it arrives as the primitive's default value or fails the request. Use the wrapper
  class or make the GraphQL type non-null. The startup fails with `IncorrectClassMappingException`. For a custom
  scalar this fails in every mode. A primitive for a non-null type and a primitive return type are fine. This finding
  has no `!` line in the manifest.
- **Applied directives without a wiring** (`!unwiredDirective name`, one line per directive): a directive the schema
  applies that no `SchemaDirective` bean, `RuntimeWiringCustomizer` or `resolver-directives` entry implements and that
  `inert-directives` does not list (see [Schema directives](#schema-directives)). They are reported in one
  `UnwiredDirectivesException`, which lists where each is applied.

All findings except unreachable types, unmapped input class properties, primitives for nullable scalars and unwired
directives are reported in one `StrictModeException`. `!ambiguous` lines are allowed by
the `precedence` that produced them. Every schema field must be bound in every mode (`MethodNotFoundException`).

With `strict: false` the library binds as before strict mode existed: only object, interface and union types are
checked for reachability, and the findings appear in the manifest only. `allow-unreachable` may still name unreachable
enum, input and scalar types, so switching strict mode off never makes a valid configuration fail; without strict mode
those entries have no effect. Unmapped input class properties are accepted (unless `input.unmapped-properties: fail`)
and primitives for nullable built-in scalars bind as before. Applied directives without a wiring are logged as one
warning that lists them as the error would, and the schema keeps them without effect. Invalid `inert-directives`
entries still fail the startup, as invalid `allow-unreachable` entries do.

## Custom scalars

GraphQL's own scalars (`String`, `Boolean`, `Int`, `Float`, `ID`) and the extended scalars `Long`, `Short`,
`BigDecimal` and `BigInteger` are built in. Any other scalar of the schema needs a `GraphQLScalarMapping` bean: the
graphql-java `GraphQLScalarType` that implements it, and the Java classes that stand for it.

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
- **Input classes** are the classes the scalar's `parseValue`/`parseLiteral` produce. An argument or an input object
  field of the scalar type must be declared as exactly one of them.
- A primitive matches its wrapper class. On the input side it is accepted only for a non-null GraphQL type
  (`Count!`), because `null` cannot be passed to it.

The library ships no scalar implementations of its own beyond the built-in ones; graphql-java-extended-scalars and
other libraries provide many. A mapping only takes effect for scalars the schema declares.

The startup fails:

- with `ScalarMappingNotProvidedException` when a field, argument or input field uses a scalar that has no mapping;
  the message names the coordinate;
- with `IncorrectClassMappingException` when the Java type is not one of the mapping's classes, when the mapping
  declares no classes for that side, or when a primitive stands for a nullable input;
- with `InvalidScalarMappingException` when two mappings have the same scalar name, or a mapping names a built-in
  scalar.

Values of custom scalars inside input objects reach the input class as the instances the scalar parsed, also for
classes Jackson cannot serialize itself (such as `java.time` types without the JSR-310 module).

## Arguments and input objects

graphql-java hands a data fetcher the coerced argument values: scalar values, enum names, a `Map` per input object
and a `List` per list. The library converts each value into the declared Java type of the resolver parameter,
including its type arguments (`List<SomeInput>`, `List<List<SomeInput>>`, `Set<SomeEnum>`), with a Jackson
`ObjectMapper`.

By default that is a private mapper with Jackson's defaults plus Micronaut's `BeanIntrospectionModule`. In particular
`FAIL_ON_UNKNOWN_PROPERTIES` stays `true`. An input field that the input class cannot take fails the startup in any
case (see [Input classes and input types](#input-classes-and-input-types)). To convert with your own settings, define an `ObjectMapper` bean named `graphql-tools`
(`GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER`):

```java
@Bean
@Singleton
@Named(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER)
ObjectMapper graphQLToolsObjectMapper() {
    return new ObjectMapper()
            .registerModule(new BeanIntrospectionModule())
            .registerModule(new JavaTimeModule());
}
```

- Only a bean with exactly this name is used; the application's default `ObjectMapper` and the `jackson.*`
  configuration do not affect input conversion.
- The library works on a copy of the bean and adds its own module to the copy (custom scalar values pass through
  unchanged, see [Custom scalars](#custom-scalars)); the bean itself is not changed.
- The bean is used as it is otherwise: register `BeanIntrospectionModule` yourself if input classes must be read
  without reflection (GraalVM native image).

### Input classes and input types

Input classes are checked against their GraphQL input types at startup, in both directions:

- Every field of an input type needs a property of the same name that the input `ObjectMapper` can set: a setter, a
  field or a constructor parameter, under the name Jackson uses. A missing property fails the startup with
  `MethodNotFoundException`; a read-only, `@JsonIgnore`d or renamed one (naming strategy, `@JsonProperty`) with
  `InputFieldNotSettableException`, which names the field as `InputType.field`. Such a field would otherwise fail every
  request that sends it, or be dropped silently by a lenient mapper. The check uses the mapper that converts requests,
  including a [named mapper](#arguments-and-input-objects); it is skipped for a class the mapper converts with a
  deserializer of its own, and the startup logs such classes at INFO.
- A property the class can set (through a setter or the constructor) that the input type does not define fails the
  startup in [strict mode](#strict-mode), all of them in one `UnmappedInputPropertiesException`, because no request can
  set it. Each one is also an `!unbound InputClass#property` line of the binding manifest.
  `micronaut.graphql.tools.input.unmapped-properties` overrides strict mode: `ignore` accepts such properties,
  `fail` rejects them also with `strict: false`. Read-only properties, such as computed getters, are not checked.

Both checks apply wherever the input type is used: an argument, a list, another input type, an `ArgumentValue`.

### Omitted values and `null`

By default an omitted argument and an explicit `null` both reach the resolver as `null` (an input class property the
client leaves out keeps the value the class gives it). Default values of the schema apply in any case, and enum values
arrive as the Java enum constants of the same name.

To tell them apart, set `micronaut.graphql.tools.input.detect-omission: true` and declare the resolver parameter or the
input class property as `ArgumentValue<T>`:

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
  type needs a getter and a setter (the library sets it to `ArgumentValue.omitted()` when the client leaves it out).
  Otherwise, and without the option, the startup fails with `UnsupportedInputTypeException`.
- After a conversion the library reads only the `ArgumentValue` properties and the properties that hold input objects
  (worked out at startup); it never calls other getters. A write-only property that holds an input class with
  `ArgumentValue` properties fails the startup, because they could not be set to `omitted()`.

## Configuration

| Property | Values (default first) | Meaning |
|---|---|---|
| `micronaut.graphql.tools.unreachable-types` | `fail` \| `ignore` | Object, interface and union types of the schema that no field reachable from a root operation type returns have no binding; in [strict mode](#strict-mode) (the default) the same holds for enum, input and scalar types that nothing uses. `fail` stops the startup with one error that lists all of them; `ignore` keeps them in the schema with graphql-java's default data fetchers and logs them as a warning. |
| `micronaut.graphql.tools.allow-unreachable` | list of type names (empty) | Unreachable types that are allowed even with `fail`; they are logged at INFO. Every entry must be an unreachable type of the schema (object, interface, union, enum, input or scalar; enum, input and scalar entries take effect in [strict mode](#strict-mode) only), otherwise the startup fails, so the list cannot go stale. |
| `micronaut.graphql.tools.precedence` | `strict` \| `property-first` \| `resolver-first` | What binds a field that both a member of its Java type and a type resolver method can bind, see [Property or type resolver](#property-or-type-resolver). `strict` fails the startup. |
| `micronaut.graphql.tools.resolver-directives` | list of directive names (empty) | A field carrying one of these directives is bound by a type resolver method, and fails the startup if none binds it. Each must be declared in the schema. |
| `micronaut.graphql.tools.strict` | `true` \| `false` | Fail the startup on the findings listed in [Strict mode](#strict-mode), among them input class properties without an input field (unless `input.unmapped-properties` is set), primitives for nullable built-in scalars and applied directives without a wiring. `false` binds as before and only lists them in the manifest (unwired directives are also logged as a warning). |
| `micronaut.graphql.tools.manifest.enabled` | `false` \| `true` | Write the [binding manifest](#binding-manifest) at startup (this binds the schema at startup). |
| `micronaut.graphql.tools.manifest.path` | file path (none) | Where to write it; without a path it is logged at INFO. |
| `micronaut.graphql.tools.input.detect-omission` | `false` \| `true` | Enables `ArgumentValue<T>` parameters and input properties, which tell an omitted value from an explicit `null`; see [Omitted values and `null`](#omitted-values-and-null). |
| `micronaut.graphql.tools.input.unmapped-properties` | not set \| `fail` \| `ignore` | Input class properties that the GraphQL input type does not define. Not set: they fail in [strict mode](#strict-mode) and are accepted without it. `fail` stops the startup with one error that lists them for every input class, also with `strict: false`; `ignore` accepts them, also in strict mode. See [Input classes and input types](#input-classes-and-input-types). |
| `micronaut.graphql.tools.inert-directives` | list of directive names, without `@` (empty) | Directives the schema may apply without a wiring, because they have no effect at runtime; they are logged at INFO. Every other applied directive needs a wiring (see [Schema directives](#schema-directives)); without one it fails the startup in [strict mode](#strict-mode) and is a warning without it. Every entry must be a directive the schema declares, that is not built in, that has no wiring and that is not a `resolver-directives` entry, otherwise the startup fails in either mode, so the list cannot go stale. |

```yaml
micronaut:
  graphql:
    tools:
      allow-unreachable:
        - LegacyReport
```

## Compile-time checks

The `micronaut-graphql-tools-processor` annotation processor reads the schema files at compile time and fails `javac`
on drift that the startup would report, so that it fails the build instead. Each error starts with `[graphql]` and the
schema coordinate, ends with the schema file and line, and is reported on the Java element where there is one:

```
error: [graphql] Report: the object type is unreachable: no field reachable from a root operation type returns it. Use it, remove it from the schema, or list it in micronaut.graphql.tools.allowUnreachable. (schema.graphqls:9)
```

Add it next to Micronaut's processor and pass the directory of the schema files as a declared input of the
compilation, so that a schema change recompiles (a plain `compilerArgs` string would leave `compileJava` up to date):

```kotlin
dependencies {
    annotationProcessor("io.micronaut.graphql.tools:micronaut-graphql-tools-processor")
}

abstract class GraphQLSchemaDir : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dir: DirectoryProperty

    override fun asArguments() = listOf("-Amicronaut.graphql.tools.sdlDir=${dir.get().asFile.absolutePath}")
}

tasks.compileJava {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDir>().apply {
        dir.set(layout.projectDirectory.dir("src/main/resources/graphql"))
    })
}
```

The processor is aggregating: it checks the whole compilation once, in the last round. It claims the library's
annotations and the Micronaut annotations that make a class a bean (`@Singleton`, `@Prototype`, `@RequestScope`, ...),
never `*`, so Gradle's incremental compilation stays incremental: after a change to one file, Gradle recompiles the
changed classes and hands every claimed type to the processor again, which checks the whole module as a full
compilation does (unchanged classes are read from the class output). Errors about unchanged files then have no Java
file and line (javac did not compile them), but name the coordinate and the class. With Micronaut's processor, set
`micronaut.processing.incremental=true` (the Micronaut Gradle plugin does), or every compilation is a full one.
It reads every `*.graphqls` and `*.graphql` file under the directory.

A processor that is not applied does not fail anything, it just does not run: under kapt, `compileJava` runs with
`-proc:none` and a processor on `annotationProcessor` is ignored, so register it with `kapt` too (and declare the schema
directory as an input of `kaptKotlin`). Keep a guard in the build that fails when the manifest is missing from the
class output (under kapt, kapt's class output, `build/tmp/kapt3/classes/main`):

```kotlin
val checkBindingManifest by tasks.registering {
    val manifest = tasks.compileJava.flatMap {
        it.destinationDirectory.file("META-INF/micronaut-graphql-tools/binding-manifest.txt")
    }
    inputs.files(tasks.compileJava)
    doLast {
        if (!manifest.get().asFile.isFile) {
            throw GradleException("The GraphQL compile-time checks did not run: ${manifest.get().asFile} is missing.")
        }
    }
}
tasks.check { dependsOn(checkBindingManifest) }
```

The library's example application (`test-suite`) is built this way, and its tests compare the manifest the
processor wrote with the one its startup builds, without what the processor's record of omissions names.

Options (`-A<name>=<value>`) have the values and defaults of the runtime options of the same name. They are written
in camel case because `javac` accepts only dot-separated identifiers as option names (`sdl-dir` is rejected). Boolean
options are read as Micronaut reads booleans (`true`, `yes`, `y`, `on`, and their opposites); any other value fails the
compilation, and so does an unknown option under `micronaut.graphql.tools.`, so that a typo cannot switch a check off:

| Option | Runtime option |
|---|---|
| `micronaut.graphql.tools.sdlDir` | none: the schema directory, required when the compilation has GraphQL resolvers or types |
| `micronaut.graphql.tools.strict` | `strict` |
| `micronaut.graphql.tools.precedence` | `precedence` |
| `micronaut.graphql.tools.resolverDirectives` | `resolver-directives` (comma-separated) |
| `micronaut.graphql.tools.allowUnreachable` | `allow-unreachable` (comma-separated) |
| `micronaut.graphql.tools.unreachableTypes` | `unreachable-types` |
| `micronaut.graphql.tools.input.unmappedProperties` | `input.unmapped-properties` |
| `micronaut.graphql.tools.input.detectOmission` | `input.detect-omission` |
| `micronaut.graphql.tools.manifest` | none: `true` (default) writes the [binding manifest](#the-manifest-as-a-build-artifact) to the class output, `false` does not |

Checked from the schema files and the options alone:

- the schema has a schema definition (`schema { query: ... }`);
- every applied directive is declared (graphql-java would fail to build the schema);
- every `resolverDirectives` entry is declared;
- unreachable types, with `allowUnreachable` and `unreachableTypes` as at startup (in strict mode also enum, input
  and scalar types), and `allowUnreachable` entries that are not unreachable types.

Checked against the Java code, walking the schema from the root operation types as the startup binds it (the Java type
a method or property returns is the Java type of the field's GraphQL type):

- every root field has exactly one root resolver method, and every field of a bound object type has a property or
  `@GraphQLField` method of the introspected Java type or a type resolver method, decided by `precedence` and
  `resolverDirectives` exactly as at startup (an ambiguous field fails with `precedence: strict`; a field with a
  resolver directive needs a type resolver method);
- the number of parameters besides `DataFetchingEnvironment`, the source object first for a type resolver, and the
  Java type of each argument in the schema's order (lists, built-in scalars, `ArgumentValue`; a primitive for a
  nullable type);
- return types: the wrappers graphql-java resolves (`CompletionStage` at field level only, `Publisher` exactly on the
  subscription type, `DataFetcherResult`, `Optional` on nullable types, each once and not raw), lists, built-in scalars;
- resolver contracts: an annotated interface of the compilation that no bean implements, or that several beans
  implement;
- `@GraphQLSchemaCoordinate`: validated as at startup (form, type and field exist, field name equals method name, root
  or type resolver) and bound to exactly the field it names;
- model classes: one Java type per schema type wherever it is reached (two classes bind an object type alike only with
  the same contract and introspection), a custom class with a bean introspection (with the properties its
  `@Introspected(includes = ..., excludes = ...)` leaves), read through the contract interface
  when it has its own introspection (getters only an implementation declares do not bind), every class annotated
  `@GraphQLType(Contract.class)` implementing the contract; in strict mode, a Java interface without its own
  introspection read through its implementation;
- enums: a Java enum with a constant for every schema value, and in strict mode no constant without one;
- input types: an introspected class with a property for every input field, of a matching type (also nested input
  types, lists and enums), and, in strict mode or with `input.unmappedProperties=fail`, no property a request can set
  (setter, record component or constructor parameter) that the input type does not define;
- unions and interfaces: the field declares a Java interface;
- in strict mode, public resolver methods that bind no field, and a root method without a coordinate that binds fields
  of several root operation types.

The processor checks the resolver beans of one compilation: apply it in the module that holds all of them (usually the
application). Contracts and model classes may come from dependencies. A compilation that has `sdlDir` but no root or
type resolver fails, rather than checking nothing. Classes that another processor generates in the same compilation
under a `$` name (Micronaut's bean definitions and AOP proxies) are not application classes and are ignored; a class
annotated `@Generated` (generated code compiled with the application) is checked like any other. Fields of type
extensions (`extend type`, `extend interface`, ...) are checked like fields of the type itself.

Not checked at compile time, because the startup decides them with runtime beans or with classes the compilation
does not have; the strict startup and a test that starts the context check them. The processor reports each such part
as a javac note (`[graphql] <coordinate>: not checked at compile time, the startup checks it: <reason>`), so that a
green compilation says what it left out:

- the Java classes of custom scalars (`GraphQLScalarMapping` beans) and scalars without a mapping;
- directive wirings and type resolvers of interfaces and unions;
- the members of unions and the implementations of interfaces, which come from the `SchemaMappingDictionary`, and
  fields an SDL interface declares when a type resolver of a Java interface of the model has a method of their name
  (without such a method, the field is checked like any other);
- a Java interface of a dependency, returned by a field, whose implementation is not in the compilation (a JDK
  interface, or one of the compilation, without an implementation fails);
- a model or input class introspected with `@Introspected(accessKind = FIELD)`, which the processor does not model;
- what the input `ObjectMapper` can set (custom deserializers, naming strategies).

### The manifest as a build artifact

A compilation that passes the checks writes the [binding manifest](#binding-manifest) to its class output as
`META-INF/micronaut-graphql-tools/binding-manifest.txt`: once, after the last round, in the format of the startup
manifest (same header, UTF-8, `LF`, body sorted by `String#compareTo`). A compilation with an error writes none, so a
manifest in the output always belongs to code that passed the checks. `-Amicronaut.graphql.tools.manifest=false`
turns it off.

For the same code, schema and options it has the lines of the startup manifest, byte for byte, except those that only
the running application knows:

- `scalar` lines, which name the `Coercing` of the scalar's runtime wiring;
- `resolves` lines, and every line of an object type bound only through the `SchemaMappingDictionary` (the members of
  unions and the implementations of interfaces that fields return), of what only those types reach, and of an
  interface only they implement;
- fields that a type resolver of an SDL interface binds, `!unusedDictionary` lines, and the `!unbound` lines of type
  resolver methods whose class only those parts may reach;
- `!unwiredDirective` lines: directive wirings are runtime beans, so whether an applied directive has one is decided
  at startup (with `inert-directives`, which has no processor option); in strict mode such a directive fails the
  startup, so these lines appear only with `strict: false`;
- the lines of a type the processor leaves to the startup for another reason (reported as a note, see above).

The processor writes what it left out next to the manifest, as
`META-INF/micronaut-graphql-tools/binding-manifest-omitted.txt`, one entry per line:

```
# graphql-binding-manifest-omitted 1
kind resolves
kind scalar
kind unusedDictionary
kind unwiredDirective
type Magazine
field Book.name
unbound example.MagazineResolver#issue(example.Magazine)
```

`kind K` stands for every line of kind `K` (a binding line `... → K · ...` or a finding `!K ...`); `type T` for every
line about `T` (`T → ...`, `T.x ...`, `T/...`, `!ambiguous T.x ...`); `field T.f` for the line of the field and
its `!ambiguous` line; `unbound M` for the line `!unbound M`. A comparison of both manifests removes from the
startup manifest exactly what this record names, and nothing else, so the omissions come from the processor, never
from a list in the build.

The header is identical when the processor and the library come from the same release (the `# binder:` line names
the version of the jar). The library's tests compile fixtures with the processor, start them, and compare both
manifests under this rule.

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
  and to make implementation-only getters non-candidates. [Strict mode](#strict-mode), on by default, rejects this
  case; it binds only with `strict: false`.

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
which graphql-java lets win), the startup fails with `RuntimeWiringOverrideException`, which names every replacement
and the customizer that made it. Code registry entries for fields the library does not wire, and enum value
providers, are allowed.
(On graphql-java 25.1 the builder itself already rejects the first replaced data fetcher, default data fetcher, type
resolver or scalar with `StrictModeWiringException`; 21.3 accepts them silently.) To wrap data fetchers, use a
`SchemaDirectiveWiring`; to change the generated schema, use a `GraphQLSchemaCustomizer` (it replaces
`RuntimeWiring.Builder#transformer`, which graphql-java 25 removed). The library does not check what a
`GraphQLSchemaCustomizer` changes.

```java
@Bean
@Singleton
@Order(10)
public RuntimeWiringCustomizer hideInternalFields() {
    return builder -> builder.fieldVisibility(BlockedFields.newBlock().addPattern("Query.internal.*").build());
}
```

## Schema directives

The library applies an SDL directive wiring (`graphql.schema.idl.SchemaDirectiveWiring`) only through a
`SchemaDirective` bean:

- `SchemaDirective.named(name, wiring)` binds the wiring to **one** directive name; graphql-java calls it only for the
  elements that apply that directive (`RuntimeWiring.Builder#directive`). Two such beans for one name fail the
  startup with `DuplicateDirectiveWiringException`, and a `RuntimeWiringCustomizer` that replaces such a wiring fails
  it with `RuntimeWiringOverrideException`.
- `SchemaDirective.everyElement(names, wiring)` registers a wiring that graphql-java calls for **every** schema element
  and that decides itself what to do (`RuntimeWiring.Builder#directiveWiring`); the names say which directives it
  implements. These run lowest order first (`@Order` or `Ordered` on the `SchemaDirective` bean), so the first one
  wraps a data fetcher innermost.

A `SchemaDirectiveWiring` bean is **not** applied on its own: if no `SchemaDirective` bean wraps that instance, the
startup fails with `UnwrappedDirectiveWiringException`, which names the bean. A wiring therefore never runs on every
element just because it is a bean. To keep a wiring as a bean, inject it into the factory method of its
`SchemaDirective`.

**Every directive the schema applies must have a wiring**, or the startup fails with `UnwiredDirectivesException`,
which lists every such directive and where the schema applies it. With `strict: false` (see [Strict mode](#strict-mode)) the
same list is a warning instead; either way each such directive is an `!unwiredDirective` line of the binding
manifest. A directive counts as wired if a `SchemaDirective` bean names it, if a `RuntimeWiringCustomizer` registers a
wiring for it with `RuntimeWiring.Builder#directive`, or if `resolver-directives` lists it (the binding implements it). A
directive that is meant to have no effect at runtime (documentation, or enforced elsewhere) is listed in
`micronaut.graphql.tools.inert-directives`. `SchemaDirectiveWiring` has no hook for the schema definition or for
directive arguments, so a directive applied there can only be listed as inert, or be handled by a
`GraphQLSchemaCustomizer`. Exempt: graphql-java's built-in directives (`@deprecated`, `@specifiedBy`, `@oneOf`,
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

Use extended-validation 21.0 with graphql-java 21.3 and 24.0 with 25.1 (21.0 calls a `GraphQLCodeRegistry` method
that graphql-java 25 removed); the library's tests run both.

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

[`test-suite/src/test/java/example/host`](test-suite/src/test/java/example/host) is such an application: it supplies the
`TypeDefinitionRegistry`, consumes only the library's `GraphQLSchema` and builds its own `GraphQL` with its own
instrumentation, which then serves the HTTP requests.

The library brings `io.micronaut.graphql:micronaut-graphql` as an API dependency, and its HTTP controller serves
whichever `GraphQL` bean exists. The library's own code does not use it, so an application with its own transport
can exclude that dependency.

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
| Annotation processor ([compile-time checks](#compile-time-checks)) | `io.micronaut.graphql.tools:micronaut-graphql-tools-processor` |
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
