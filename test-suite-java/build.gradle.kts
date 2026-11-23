plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    id("io.micronaut.aot")
    id("com.apollographql.apollo")
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
    implementation(libs.apollo.java.client)
    implementation(libs.apollo.java.rx3)
    implementation(libs.apollo.api) // the Java client 1.0.0 resolves apollo-api 5.0.1; keep the API at the plugin's version
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
    // The security module and Micronaut Security: the principal in the context, @Secured on resolvers and the root fields hook, tested with
    // a real HTTP and WebSocket client. Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsSecurity)
    testImplementation(mnSecurity.micronaut.security)
    testImplementation("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
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

apollo {
    service("service") {
        packageName.set("example.client")
        schemaFiles.from("src/main/resources/schema.graphqls")
        generateKotlinModels.set(false)
    }
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
// validation artifacts are not in the `mn` catalog (the platform BOM manages them), so no version here.
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
    "validationFixturesImplementation"("io.micronaut.validation:micronaut-validation")
    "validationFixturesAnnotationProcessor"("io.micronaut.validation:micronaut-validation-processor")
    "validationFixturesAnnotationProcessor"(projects.micronautGraphqlToolsProcessor)
    testImplementation(validationFixtures.output)
    testImplementation("io.micronaut.validation:micronaut-validation")
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

// The Micronaut Serialization stack has its own test source set and task: micronaut-serde-jackson makes its JsonMapper
// win over Jackson databind, so the two stacks cannot share one test classpath. `check` runs it; its tests are raw-HTTP
// checks that a response keeps its null fields (#45). `nativeSerdeTest` runs the same classes as a native binary.
val serdeTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(serdeTest.implementationConfigurationName) { extendsFrom(testImplementation.get()) }
    named(serdeTest.runtimeOnlyConfigurationName) { extendsFrom(testRuntimeOnly.get()) }
    named(serdeTest.annotationProcessorConfigurationName) { extendsFrom(testAnnotationProcessor.get()) }
}
dependencies {
    "serdeTestImplementation"("io.micronaut.serde:micronaut-serde-jackson")
    "serdeTestAnnotationProcessor"("io.micronaut.serde:micronaut-serde-processor")
}
val serdeTestTask = tasks.register<Test>("serdeTest") {
    description = "Runs the tests on Micronaut Serialization (micronaut-serde-jackson)."
    group = "verification"
    testClassesDirs = serdeTest.output.classesDirs
    classpath = serdeTest.runtimeClasspath
    useJUnitPlatform()
}
tasks.named("check") { dependsOn(serdeTestTask) }
graalvmNative {
    registerTestBinary("serdeTest") {
        usingSourceSet(serdeTest)
        forTestTask(serdeTestTask)
    }
}
// The native-image plugin builds every test binary's image classpath from `testRuntimeClasspath`, which has no
// micronaut-serde: without this the image has no Serde bean definitions and selects JacksonDatabindMapper (#285).
configurations.named("nativeImageSerdeTestClasspath") { extendsFrom(configurations.getByName(serdeTest.runtimeClasspathConfigurationName)) }

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
