# micronaut-graphql-tools-security

Gradle project `:micronaut-graphql-tools-security`, folder `graphql-tools-security/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-security`.

## Purpose

The Micronaut Security integration of the library: the principal of the request in the resolvers, `@Secured` on resolver
methods, and a hook that sees the root fields of an operation. Micronaut Security is compile-only here, so the core
library does not depend on it and the beans of the module exist only when the application has it.

## Responsibility

- One `GraphQLContextContributor` that puts the `Authentication` of the request (the HTTP request, or the upgrade request
  of a WebSocket connection) into the `GraphQLContext` under `GraphQLSecurity.AUTHENTICATION_KEY`, once for each execution.
- One `GraphQLFieldFetcherDecorator` that decides, at startup, which fields `@Secured` guards. A field without it, or with
  `isAnonymous()`, keeps the fetcher it was offered. A guarded field gets a fetcher that rejects an anonymous call with
  `UNAUTHENTICATED` and a call without the role with `FORBIDDEN`, as an error of the field.
- `GraphQLRootFieldsHook`: a bean interface, run by one `Instrumentation` that exists only when a hook bean does.
- `micronaut.graphql.tools.security.enabled` switches the contributor, the decorator and the instrumentation of the hook off.
- The decorator checks roles through the `RolesFinder` bean of the context, so a replaced bean decides; an expression in `@Secured` is rejected at startup with a `ConfigurationException` that names the field.

Not in this module: authentication itself (Micronaut Security), the principal and request parameters of a resolver
(`graphql-tools`, see the guide page on resolver parameters) and the URL rules of the GraphQL path.

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.security` | Public API | `GraphQLSecurity` (the prefix, the context key, the error codes) and `GraphQLRootFieldsHook` |
| `...security.internal` | Internal | The contributor, the decorator and fetcher of the secured fields and the instrumentation of the hook |

## Who uses it

Applications that use `micronaut-graphql-tools` with Micronaut Security.

## Public and internal

- `GraphQLSecurity` and `GraphQLRootFieldsHook` are the public types.
- Every class under `io.micronaut.graphql.tools.security.internal` is `@Internal`.
- The behaviour and the cost are in the guide page on security (`src/main/docs/guide/security.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`) and uses its public SPI only (`GraphQLContextContributor`, `GraphQLFieldFetcherDecorator`).
- `micronaut-security` is `compileOnly`; its version comes from the `micronaut-security` catalog.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and run the security checks with a real HTTP and
  WebSocket client, on the JVM and, for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-security:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`:
`SecuritySpec` starts a server with Micronaut Security and calls it over HTTP and WebSocket, plus the architecture rules
and `DocsPageSpec`.
