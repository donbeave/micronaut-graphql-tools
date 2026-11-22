# micronaut-graphql-tools-upload

Gradle project `:micronaut-graphql-tools-upload`, folder `graphql-tools-upload/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-upload`.

## Purpose

File upload for GraphQL on Micronaut 4: the `Upload` scalar and a route that implements the GraphQL multipart request
specification. Add the module and declare `scalar Upload` in the schema; the mapping and the route are beans.

## Responsibility

- One `GraphQLScalarMapping` bean for `Upload`, bound to `CompletedFileUpload` and `CompletedPart`, at the top level of the
  arguments and inside input objects (also in lists). It is input only.
- A `multipart/form-data` route on the path of the GraphQL endpoint. It reads the `operations` and `map` parts, puts the
  file parts into the variables at the mapped paths and calls the `GraphQLInvocation` bean. It has no `@ExecuteOn`.
- An invalid request is a `400` with an error that carries `extensions.code` `INVALID_MULTIPART_REQUEST`.
- `micronaut.graphql.tools.upload.enabled` switches the scalar and the route off.

Not in this module: the transport route (`micronaut-graphql`), the execution hop (`graphql-tools`, the library's
invocation) and the multipart handling itself (Micronaut: `micronaut.server.multipart.*`).

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.upload` | Public API | `GraphQLUpload`: the scalar name, the configuration prefix and the error code |
| `...upload.internal` | Internal | The route, the reader of the multipart parts, the scalar and its factory |

## Who uses it

Applications that use `micronaut-graphql-tools` and accept files in a mutation.

## Public and internal

- `GraphQLUpload` is the only public type; it holds constants.
- Every class under `io.micronaut.graphql.tools.upload.internal` is `@Internal`.
- The behaviour, the limits and the cost are in the guide page on file upload (`src/main/docs/guide/fileUpload.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`) and on `micronaut-json-core` (`api`) for the `JsonMapper`. The HTTP server API
  comes with `micronaut-graphql`. No Jackson, no JSON implementation.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and post real multipart requests, on the JVM and,
  for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-upload:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`:
`MultipartUploadSpec` starts a server and posts real multipart requests with Micronaut's HTTP client (the request shapes,
the invalid requests, the route precedence and the executor), plus the architecture rules and `DocsPageSpec`.
