plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The in-process tester for application tests. It runs a document through the GraphQLInvocation bean of the application, so it needs
// the library (api) and nothing else: no test framework, no HTTP client and no JSON implementation. Applications add the module with
// testImplementation.
dependencies {
    api(projects.micronautGraphqlTools)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

// DocsPageSpec reads the guide page of the module behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("src/main/docs/guide/testing.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testingPage")
}
