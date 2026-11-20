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
    // the visitor records the contracts that a type resolver bean inherits (#34), see below
    testAnnotationProcessor(projects.micronautGraphqlToolsProcessor)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
    // ArchitectureRulesSpec: the reflection, Jackson and public-API rules read the bytecode of the library and the binding module.
    testImplementation(libs.archunit)
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
    // SingleValuePublisherSpec: the RxJava single-value types are adapted through Micronaut's reactive type converters (#428)
    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3")
    testImplementation("io.micronaut.rxjava2:micronaut-rxjava2")
    testAnnotationProcessor("io.micronaut.serde:micronaut-serde-processor")
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}

// The inherited-resolver specs need the contracts that the visitor of the processor writes into the annotation metadata of a bean
// class (the registry reads no class annotation by reflection, #34). The tests compile with the checks off: the specs bind resolvers
// that drift from every schema on purpose, and the visitor records the contracts either way. The processor is not a test
// dependency: ArchitectureRulesSpec reads the classes of the test runtime classpath, which must hold no compiler plug-in.
val visitor by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}
dependencies {
    visitor(projects.micronautGraphqlToolsProcessor)
}
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-Amicronaut.graphql.tools.processor.checks=false")
}
// The Groovy compiler reads its options from system properties, so it runs in its own JVM (the default).
tasks.named<GroovyCompile>("compileTestGroovy") {
    classpath = classpath + visitor
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.checks=false")
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
