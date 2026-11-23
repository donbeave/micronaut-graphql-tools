plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// Request and field spans (OpenTelemetry) of the executions. OpenTelemetry is not a dependency of the module's consumers: it is
// compile-only here, and the beans that use it exist only when the application has it and an OpenTelemetry bean. The versions
// come from the micronaut-tracing catalog. The micrometer module is a test dependency only, to test both modules together.
dependencies {
    api(projects.micronautGraphqlTools)
    compileOnly(mnTracing.micronaut.tracing.opentelemetry)

    testImplementation(projects.micronautGraphqlToolsMicrometer)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry.http)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry.annotation)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(platform(mnTracing.boms.opentelemetry))
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnTracing.micronaut.tracing.opentelemetry.annotation)
    testRuntimeOnly(mn.micronaut.jackson.databind)
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

// DocsPageSpec reads the guide page of the module behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("src/main/docs/guide/observability.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("observabilityPage")
}
