plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    id("io.micronaut.aot")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    // The standard scalars: java.time, JSON and the extended scalars (#46)
    implementation(projects.micronautGraphqlToolsScalars)
    // The JSON codec of the application. The library no longer brings Jackson (#36), so the default stack declares it here.
    runtimeOnly(mn.micronaut.jackson.databind)
    testImplementation(projects.testSuiteTck) // the execution-behaviour TCK (#53)
    // The metrics and tracing modules and the libraries their tests observe with: the Micrometer and OpenTelemetry integrations of
    // Micronaut, and an in-memory span exporter (#24). Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsMicrometer)
    testImplementation(projects.micronautGraphqlToolsOpentelemetry)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testAnnotationProcessor(mnMicrometer.micronaut.micrometer.annotation)
    // The upload module: the Upload scalar and the multipart route, tested with a real multipart client (#47). Test scope: the application
    // itself does not depend on it.
    testImplementation(projects.micronautGraphqlToolsUpload)
    // The in-process tester: runs documents against the application's GraphQL bean in a test. Test scope: the application itself does not depend
    // on it, and as test-time code it has no native test.
    testImplementation(projects.micronautGraphqlToolsTesting)
    // The security module and Micronaut Security: the principal in the context, @Secured on resolvers and the root fields hook, tested with
    // a real HTTP and WebSocket client. Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsSecurity)
    testImplementation(mnSecurity.micronaut.security)
    testImplementation(mn.micronaut.http.client)
    // The graphql-binding management endpoint (#515) is served only with Micronaut Management on the classpath. Test scope: the application itself does not depend on it.
    testImplementation(mn.micronaut.management)
    runtimeOnly(mnLogging.logback.classic)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("example.Application")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

graalvmNative.toolchainDetection = false
// Netty 4.2 on Java 25 releases direct buffers through a shared arena. An image without shared-arena support throws on the event-loop
// thread that releases a buffer, and that thread never terminates (#568).
graalvmNative.binaries.configureEach { buildArgs.add("-H:+SharedArenaSupport") }

micronaut {
    // The platform version: micronaut-build reads `micronaut` as the core BOM version, which has no platform of the same number.
    version.set(libs.versions.micronaut.platform)
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("example.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "25"
}

// The compile-time checks (#291): the processor registers the visitor, which checks the resolver classes against the schema while
// they compile. The schema directory is a declared input of the compilation, so that a schema edit recompiles. `module.dir` is the
// project directory: a compilation that forgot the options in the same JVM fails instead of reading these.
dependencies {
    annotationProcessor(projects.micronautGraphqlToolsProcessor)
}

abstract class GraphQLSchemaDirs : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dirs: ConfigurableFileCollection

    @get:Internal
    abstract val moduleDir: DirectoryProperty

    override fun asArguments() = listOf(
        "-Amicronaut.graphql.tools.processor.schema.dirs=" + dirs.files.joinToString(File.pathSeparator) { it.absolutePath },
        "-Amicronaut.graphql.tools.processor.module.dir=" + moduleDir.get().asFile.absolutePath)
}

val graphqlSchemaDirs = files("src/main/resources")

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDirs>().apply {
        dirs.from(graphqlSchemaDirs)
        moduleDir.set(layout.projectDirectory)
    })
}

// The Micronaut Serialization stack: the library's GraphQL client needs Micronaut Serialization, and micronaut-serde-jackson makes its
// JsonMapper win over the Jackson databind of this application, so the two stacks cannot share a classpath (test-suite-serde has the
// same rule). The tests that call the application with the client, and the declarative client with its result models, are in the source
// set `serdeTest`, which has its own task and its own native binary; `test` keeps the Jackson databind stack.
// The processor checks each operation of the declarative client against the schema of the application (the reserved client schema
// `server`, documents in src/serdeTest/graphql/client/server) and compiles it.
abstract class GraphQLClientDirs : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDirs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val clientDirs: ConfigurableFileCollection

    @get:Internal
    abstract val moduleDir: DirectoryProperty

    override fun asArguments() = listOf(
        "-Amicronaut.graphql.tools.processor.checks=true",
        "-Amicronaut.graphql.tools.processor.schema.dirs=" + schemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath },
        "-Amicronaut.graphql.tools.processor.client.dirs=" + clientDirs.files.joinToString(File.pathSeparator) { it.absolutePath },
        "-Amicronaut.graphql.tools.processor.client.checks=true",
        "-Amicronaut.graphql.tools.processor.module.dir=" + moduleDir.get().asFile.absolutePath)
}

// The beans of the environment `host-graphql`, which a test of `test` and a test of `serdeTest` both start the application with
val sharedFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(sharedFixtures.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(sharedFixtures.annotationProcessorConfigurationName) { extendsFrom(testAnnotationProcessor.get()) }
}
dependencies {
    testImplementation(sharedFixtures.output)
}

val serdeTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(serdeTest.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(serdeTest.annotationProcessorConfigurationName) { extendsFrom(testAnnotationProcessor.get()) }
}
dependencies {
    "serdeTestImplementation"(sharedFixtures.output)
    "serdeTestImplementation"(projects.micronautGraphqlToolsClient)
    "serdeTestImplementation"(mnSerde.micronaut.serde.jackson)
    "serdeTestImplementation"(mn.micronaut.http.client)
    "serdeTestImplementation"(mn.micronaut.retry)
    "serdeTestImplementation"(mnTest.micronaut.test.junit5)
    "serdeTestAnnotationProcessor"(mnSerde.micronaut.serde.processor)
    "serdeTestAnnotationProcessor"(projects.micronautGraphqlToolsProcessor)
    "serdeTestRuntimeOnly"(mnLogging.logback.classic)
    "serdeTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "serdeTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
tasks.named<JavaCompile>(serdeTest.compileJavaTaskName) {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLClientDirs>().apply {
        schemaDirs.from(graphqlSchemaDirs)
        clientDirs.from(files("src/serdeTest/graphql/client"))
        moduleDir.set(layout.projectDirectory)
    })
}
val serdeTestTask = tasks.register<Test>("serdeTest") {
    description = "Runs the tests on the Micronaut Serialization stack: the library client against this application."
    group = "verification"
    testClassesDirs = serdeTest.output.classesDirs
    classpath = serdeTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}
tasks.named("check") { dependsOn(serdeTestTask) }
graalvmNative.registerTestBinary("serdeTest") {
    usingSourceSet(serdeTest)
    forTestTask(serdeTestTask)
}
// The test binary takes the class path of the `test` source set by default, which has the Jackson databind stack and no client: the
// class path of this binary is the runtime class path of `serdeTest`, with the JUnit launcher of the native build tools, which the
// plugin resolves in `nativeImageSerdeTestClasspathInternal`.
graalvmNative.binaries.named("serdeTest") {
    classpath.setFrom(serdeTest.runtimeClasspath, serdeTest.output, provider {
        configurations.getByName("nativeImageSerdeTestClasspathInternal").filter { it.path.contains("org.graalvm.buildtools") }
    })
}

// A class that drifts from the schema on purpose, compiled with the processor and the options of `main`: driftFixtureTest asserts
// that the compilation fails with the expected `[code] coordinate` line.
val driftFixture: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(driftFixture.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(driftFixture.annotationProcessorConfigurationName) { extendsFrom(annotationProcessor.get()) }
}
tasks.named<JavaCompile>(driftFixture.compileJavaTaskName) {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDirs>().apply {
        dirs.from(graphqlSchemaDirs)
        moduleDir.set(layout.projectDirectory)
    })
}
extra["driftCompileTask"] = driftFixture.compileJavaTaskName
extra["driftFailingTask"] = driftFixture.compileJavaTaskName
extra["driftInputs"] = files(fileTree("src/driftFixture"), driftFixture.compileClasspath, driftFixture.annotationProcessorPath,
    graphqlSchemaDirs)
apply(from = rootProject.file("config/drift-fixture.gradle.kts"))

// The resolver fixtures of the constraint-violations scenario (#468), compiled with Micronaut Validation's processor and its own
// source set: that processor re-annotates every class that has @Introspected through a meta-annotation, which drops the `classes`
// of @GraphQLModels, so it cannot share a compilation with the external-model fixtures. The output is on the test classpath. The
// validation artifacts come from the micronaut-validation catalog.
val validationFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(validationFixtures.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(validationFixtures.annotationProcessorConfigurationName) { extendsFrom(testAnnotationProcessor.get()) }
}
// The graphql-tools processor runs here too (#528): @GraphQLModels must keep its introspections next to Micronaut Validation's
// processor. The resolvers of this source set are completed by a schema customizer, so the schema checks are off.
tasks.named<JavaCompile>(validationFixtures.compileJavaTaskName) {
    options.compilerArgs.add("-Amicronaut.graphql.tools.processor.checks=false")
}
dependencies {
    "validationFixturesImplementation"(mnValidation.micronaut.validation)
    "validationFixturesAnnotationProcessor"(mnValidation.micronaut.validation.processor)
    "validationFixturesAnnotationProcessor"(projects.micronautGraphqlToolsProcessor)
    testImplementation(validationFixtures.output)
    testImplementation(mnValidation.micronaut.validation)
}

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(libs.versions.groovy.get())
            because("Groovy 4.0.33 reads JDK 27 class files; the BOM's 4.0.28 does not")
        }
    }
}

val checkSuiteSchemaParity by tasks.registering {
    val reference = layout.projectDirectory.file("src/main/resources/schema.graphqls")
    val copies = listOf("test-suite-kotlin", "test-suite-groovy").map {
        rootProject.layout.projectDirectory.file("$it/src/main/resources/schema.graphqls")
    }
    val marker = layout.buildDirectory.file("checks/schema-parity.ok")
    inputs.files(reference, *copies.toTypedArray())
    outputs.file(marker)
    doLast {
        copies.forEach { copy ->
            if (copy.asFile.readText() != reference.asFile.readText()) {
                throw GradleException("${copy.asFile} differs from ${reference.asFile}: the three test suites serve one schema.")
            }
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
tasks.named("check") { dependsOn(checkSuiteSchemaParity) }

// The client examples of the guide exist in the three suites with the same class names and tags (snippet:: includes them by class name
// and tag), and the three suites compile the same client documents.
val checkClientExampleParity by tasks.registering {
    val suites = mapOf("java" to "java", "kotlin" to "kt", "groovy" to "groovy").mapKeys { (language, _) ->
        rootProject.layout.projectDirectory.dir("test-suite-$language/src/serdeTest")
    }
    val marker = layout.buildDirectory.file("checks/client-example-parity.ok")
    inputs.files(suites.keys.map { fileTree(it) }).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(marker)
    doLast {
        val tag = Regex("""tag::([\w-]+)\[]""")
        fun Map.Entry<Directory, String>.examples(): Map<String, Set<String>> {
            val root = key.dir("${if (value == "kt") "kotlin" else value}/example/client").asFile
            return root.walkTopDown().filter { it.isFile && it.extension == value }.associate { file ->
                file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".${value}") to
                    tag.findAll(file.readText()).map { it.groupValues[1] }.toSortedSet()
            }
        }
        fun Map.Entry<Directory, String>.documents(): Map<String, String> {
            val root = key.dir("graphql/client").asFile
            return root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        }
        val reference = suites.entries.first()
        suites.entries.drop(1).forEach { suite ->
            val language = suite.key.asFile.parentFile.parentFile.name
            if (suite.examples() != reference.examples()) {
                throw GradleException("$language: the client example classes and tags differ from test-suite-java: " +
                    "${suite.examples()} against ${reference.examples()}")
            }
            if (suite.documents() != reference.documents()) {
                throw GradleException("$language: the client documents differ from test-suite-java")
            }
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
tasks.named("check") { dependsOn(checkClientExampleParity) }

// The schema index of the discovery test (SchemaDiscoveryTest): a native image cannot list a classpath directory, so the
// wildcard location reads this file. The same task is in the Schema page of the guide.
val graphqlSchemaIndex = tasks.register("graphqlSchemaIndex") {
    val schemaFiles = fileTree("src/test/resources") { include("discovery/**/*.graphqls") }
    val output = layout.buildDirectory.dir("generated/graphql-index")
    inputs.files(schemaFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
    doLast {
        val index = output.get().file("META-INF/micronaut-graphql-tools/schema-index.txt").asFile
        index.parentFile.mkdirs()
        index.writeText(schemaFiles.files.map { it.relativeTo(file("src/test/resources")).invariantSeparatorsPath }
            .sorted().joinToString("\n", prefix = "# GraphQL schema files\n", postfix = "\n"))
    }
}
sourceSets.test { resources.srcDir(graphqlSchemaIndex) }

apply(from = rootProject.file("config/compile-time-report.gradle.kts"))
