plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    api(projects.micronautGraphqlToolsBinding)
    api(mn.micronaut.jackson.databind)
    api(mnGraphql.micronaut.graphql)
    api(mnGraphql.graphql.java.extended.scalars)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
    testImplementation("ch.qos.logback:logback-classic")
    testImplementation(libs.graphql.java.datetime)
    testImplementation(libs.graphql.java.extended.validation)
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}

// Warnings in the library's main code fail the build. Tests, the test-suite modules and the processor do not.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Werror")
}

// DocsCoverageSpec reads the README, the guide and the snippet sources next to this module behind Gradle's back, so
// they are declared as inputs: a documentation change must re-run the spec.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("README.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("docsReadme")
    // ModuleReadmesSpec compares the module map with settings.gradle.kts and reads each module's README.
    inputs.files(repositoryRoot.file("settings.gradle.kts"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("settingsFile")
    inputs.files(repositoryRoot.asFileTree.matching { include("*/README.md") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("moduleReadmes")
    inputs.files(repositoryRoot.asFileTree.matching { include("src/main/docs/guide/**", "test-suite-*/src/main/**", "test-suite-*/src/test/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("docsSources")
}
