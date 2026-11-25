plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The Upload scalar and the multipart route of the GraphQL multipart request specification. The module depends on the JSON API
// (to read the operations and the map) and on the HTTP server API (the multipart types); no JSON implementation and never Jackson.
dependencies {
    api(projects.micronautGraphqlTools)
    api(mn.micronaut.json.core)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.archunit)
    testImplementation(mn.groovy.json)
    // The specs start a real server and post real multipart requests. The JSON stack is Micronaut Serialization, and no Jackson
    // databind is on the test classpath. The Serde version comes from the micronaut-serde catalog.
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testRuntimeOnly(mnLogging.logback.classic)
}

// DocsPageSpec reads the guide page of the module behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.file("src/main/docs/guide/fileUpload.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("fileUploadPage")
}
