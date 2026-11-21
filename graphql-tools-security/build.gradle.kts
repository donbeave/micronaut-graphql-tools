plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

// The Micronaut Security integration: the principal in the context of an execution, @Secured on resolver methods and a hook
// that sees the root fields of an operation. Micronaut Security is compile-only here: the beans of the module exist only when
// the application has it. The version comes from the micronaut-security catalog.
dependencies {
    api(projects.micronautGraphqlTools)
    compileOnly(mnSecurity.micronaut.security)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(mnSecurity.micronaut.security)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
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
    inputs.files(repositoryRoot.file("src/main/docs/guide/security.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("securityPage")
}
