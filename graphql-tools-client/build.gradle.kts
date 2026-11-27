plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The GraphQL client (#683, #684): a runtime library for applications that call a GraphQL server. It is pure Java and compiles with
// --release 17. The API names no graphql-java, graphql-tools, Jackson or Reactor type; the HTTP client and the Serde API are
// its api dependencies, and the WebSocket and retry modules are compile-only, so an application adds them to use those parts.
dependencies {
    api(mn.micronaut.http.client.core)
    api(mnSerde.micronaut.serde.api)
    compileOnly(mn.micronaut.websocket)
    compileOnly(mn.micronaut.retry)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    // The codec specs run on Micronaut Serialization with its JSON implementation, and no Jackson databind is on the test classpath.
    testImplementation(mnSerde.micronaut.serde.jackson)
    // Models.java: polymorphic results use the Jackson annotations that Micronaut Serialization reads; annotations only, no databind
    testImplementation(mn.jackson.annotations)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testRuntimeOnly(mnLogging.logback.classic)
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
