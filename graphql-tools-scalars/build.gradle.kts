plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The standard scalars: java.time, JSON and a selection of the extended scalars, registered as GraphQLScalarMapping beans.
// The JSON scalar converts values with the application's JsonMapper (Micronaut Serialization in an application that uses it);
// this module depends on the JSON API only and on no implementation, and never on Jackson.
dependencies {
    api(projects.micronautGraphqlTools)
    api(mnGraphql.graphql.java.extended.scalars)
    api(mn.micronaut.json.core)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    // ScalarsArchitectureSpec: the package and Jackson rules read the bytecode of this module.
    testImplementation(libs.archunit)
    // The JSON scalar specs run on Micronaut Serialization, and no Jackson databind is on the test classpath. The Serde version
    // comes from the micronaut-serde catalog.
    testImplementation(mnSerde.micronaut.serde.jackson)
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testRuntimeOnly(mnLogging.logback.classic)
}

// DocsPageSpec reads the guide page of the scalars behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("src/main/docs/guide/scalars.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("scalarsPage")
}
