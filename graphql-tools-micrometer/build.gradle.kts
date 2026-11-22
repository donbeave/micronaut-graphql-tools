plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

// Request metrics (Micrometer) of the executions. Micrometer is not a dependency of the module's consumers: it is compile-only here,
// and the observer bean exists only when the application has it and a MeterRegistry bean. The version comes from the
// micronaut-micrometer catalog.
dependencies {
    api(projects.micronautGraphqlTools)
    compileOnly(mnMicrometer.micronaut.micrometer.core)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnMicrometer.micronaut.micrometer.annotation)
    testAnnotationProcessor(mn.micronaut.inject.java)
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
