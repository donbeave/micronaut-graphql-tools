plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

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
    testImplementation("org.apache.groovy:groovy-json")
    // The specs start a real server and post real multipart requests. The JSON stack is Micronaut Serialization, and no Jackson
    // databind is on the test classpath. The Serde version comes from the Micronaut platform BOM, as in graphql-tools.
    testImplementation(platform(libs.micronaut.platform))
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation("io.micronaut.serde:micronaut-serde-jackson")
    testAnnotationProcessor(platform(libs.micronaut.platform))
    testAnnotationProcessor("io.micronaut.serde:micronaut-serde-processor")
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
    inputs.files(repositoryRoot.file("src/main/docs/guide/fileUpload.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("fileUploadPage")
}
