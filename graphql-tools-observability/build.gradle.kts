plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

// Request metrics (Micrometer) and traces (OpenTelemetry) of the executions. Neither library is a dependency of the module's
// consumers: each is compile-only here, and the beans that use it exist only when the application has it (and a MeterRegistry
// or OpenTelemetry bean). The versions come from the micronaut-micrometer and micronaut-tracing catalogs.
dependencies {
    api(projects.micronautGraphqlTools)
    compileOnly(mnMicrometer.micronaut.micrometer.core)
    compileOnly(mnTracing.micronaut.tracing.opentelemetry)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry.http)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry.annotation)
    testImplementation(mnMicrometer.micronaut.micrometer.annotation)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(platform(mnTracing.boms.opentelemetry))
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnTracing.micronaut.tracing.opentelemetry.annotation)
    testAnnotationProcessor(mnMicrometer.micronaut.micrometer.annotation)
    testRuntimeOnly(mn.micronaut.jackson.databind)
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}

// DocsPageSpec reads the guide page of the module behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("src/main/docs/guide/observability.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("observabilityPage")
}
