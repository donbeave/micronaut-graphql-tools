plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

// Request metrics (Micrometer) and traces (OpenTelemetry) of the executions. Neither library is a dependency of the module's
// consumers: each is compile-only here, and the beans that use it exist only when the application has it (and a MeterRegistry
// or OpenTelemetry bean).
dependencies {
    api(projects.micronautGraphqlTools)
    compileOnly(platform(libs.micronaut.platform))
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.opentelemetry:opentelemetry-api")

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(platform(libs.micronaut.platform))
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testAnnotationProcessor(platform(libs.micronaut.platform))
    testAnnotationProcessor(mn.micronaut.inject.java)
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
