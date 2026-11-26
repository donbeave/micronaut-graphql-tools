# micronaut-graphql-tools-client

Gradle project `:micronaut-graphql-tools-client`, folder `graphql-tools-client/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-client`.

## Purpose

The GraphQL client of the library for Micronaut 4: the runtime that an application uses to call a GraphQL server. The module
is the skeleton of that client. It holds the packages, the dependencies and the check that keeps the server stack out; the
client types arrive with the client phases.

## Responsibility

- The public packages of the client: `io.micronaut.graphql.tools.client` (the root), `...client.annotation` and
  `...client.exceptions`. They hold no types yet.
- The internal packages: `...client.internal` (the call path), `...client.internal.serde` (the Micronaut Serialization codec of
  the response envelope) and `...client.internal.ws` (the WebSocket transport).
- The `checkNoGraphQLJava` task, part of `check`: it fails when graphql-java or the graphql-tools runtime is on a main
  classpath of this module.

Not in this module: the server (`graphql-tools`), the transport of the server (`micronaut-graphql`), and the native tests of
the client (#681).

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.client` | Public API | The root of the client API; no types yet |
| `...client.annotation` | Public API | The annotations of a client; no types yet |
| `...client.exceptions` | Public API | The exceptions of a client call; no types yet |
| `...client.internal` | Internal | The call path and the wiring of the client beans |
| `...client.internal.serde` | Internal | The Serde codec of the response envelope |
| `...client.internal.ws` | Internal | The WebSocket transport, present with the Micronaut WebSocket client |

## Who uses it

Applications that call a GraphQL server from Micronaut, and do not serve GraphQL themselves.

## Public and internal

- The public packages hold the API. Every class under `io.micronaut.graphql.tools.client.internal` is `@Internal`.
- The API names no graphql-java, graphql-tools, Jackson or Reactor type.

## Relation to other modules

- Depends on `micronaut-http-client-core` (`api`) and on the Micronaut Serialization API (`api`, `micronaut-serde-api`).
- Depends on the Micronaut WebSocket client and the retry module at compile time only (`compileOnly`); an application adds them
  to use the subscriptions and the retries.
- It does not depend on `graphql-tools`, `graphql-tools-binding` or `graphql-tools-processor`. `checkNoGraphQLJava` keeps it so.
- It is published with the other library modules and managed by `graphql-tools-bom`.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-client:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. It has no tests yet. `checkNoGraphQLJava` runs as
part of `check`.
