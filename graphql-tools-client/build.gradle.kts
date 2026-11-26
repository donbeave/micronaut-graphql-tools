plugins {
    id("io.micronaut.build.internal.graphql-tools-plain-module")
}

// The GraphQL client (#683): a runtime library for applications that call a GraphQL server. It is pure Java and compiles with
// --release 17. The API names no graphql-java, graphql-tools, Jackson or Reactor type; the HTTP client and the Serde API are
// its api dependencies, and the WebSocket and retry modules are compile-only, so an application adds them to use those parts.
dependencies {
    api(mn.micronaut.http.client.core)
    api(mnSerde.micronaut.serde.api)
    compileOnly(mn.micronaut.websocket)
    compileOnly(mn.micronaut.retry)
}

// The client runs in an application that calls a server, so it must not bring the server stack: no graphql-java and no
// graphql-tools runtime on either main classpath. The check reads the resolved components, as checkNoRuntimeDependency does.
val checkNoGraphQLJava by tasks.registering {
    description = "Fails when a main classpath of this module contains graphql-java or the graphql-tools runtime."
    group = "verification"
    val components = listOf("compileClasspath", "runtimeClasspath").map { name ->
        configurations.named(name).map { configuration ->
            configuration.incoming.resolutionResult.allComponents.map { it.id.displayName }
        }
    }
    inputs.property("components", providers.provider { components.flatMap { it.get() } })
    doLast {
        val forbidden = components.flatMap { it.get() }.filter {
            it.startsWith("com.graphql-java:") || it == "project :graphql-tools" || it.startsWith("io.micronaut.graphql.tools:micronaut-graphql-tools:")
        }
        check(forbidden.isEmpty()) { "graphql-tools-client must not depend on graphql-java or the graphql-tools runtime, found: $forbidden" }
    }
}
tasks.named("check") {
    dependsOn(checkNoGraphQLJava)
}
