plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The quality plugin checks the headers of src/main only; this checks the test sources.
extra["licenseSourceSets"] = "test"
apply(from = rootProject.file("config/spotless.license.gradle"))

dependencies {
    api(projects.micronautGraphqlToolsBinding)
    api(mnGraphql.micronaut.graphql)
    api(mnGraphql.graphql.java.extended.scalars)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
    // InputPropertiesSpec: a Jackson annotation on an input class has no effect (BC-23). Annotations only, no databind.
    testImplementation("com.fasterxml.jackson.core:jackson-annotations")
    // DocsCoverageSpec reads spring-configuration-metadata.json with groovy.json.JsonSlurper
    testImplementation("org.apache.groovy:groovy-json")
    testImplementation("ch.qos.logback:logback-classic")
    testImplementation(libs.graphql.java.datetime)
    testImplementation(libs.graphql.java.extended.validation)
    // SerdeInputMutationSpec: an input class with Micronaut Serialization, and no Jackson databind on the test classpath (#36).
    // The Serde version comes from the Micronaut platform BOM, as in graphql-tools-tck.
    testImplementation(platform(libs.micronaut.platform))
    testAnnotationProcessor(platform(libs.micronaut.platform))
    testImplementation("io.micronaut.serde:micronaut-serde-api")
    testAnnotationProcessor("io.micronaut.serde:micronaut-serde-processor")
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
    // PackageDocumentationSpec lists the Java packages of the library modules and compares them with the API map.
    inputs.files(repositoryRoot.asFileTree.matching { include("graphql-tools*/src/main/java/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("moduleSources")
    inputs.files(repositoryRoot.asFileTree.matching { include("src/main/docs/guide/**", "test-suite-*/src/main/**", "test-suite-*/src/test/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("docsSources")
    // NoPlanningReferencesSpec reads every text file of the tree, so any edit to the tree re-runs it.
    inputs.files(repositoryRoot.asFileTree.matching { exclude("**/build/**", "**/.git/**", "**/.gradle/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("planningReferenceScan")
}
