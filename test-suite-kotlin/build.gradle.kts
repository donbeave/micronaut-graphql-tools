plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
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
    implementation(mnKotlin.micronaut.kotlin.runtime)
    implementation(mn.kotlin.stdlib)
    implementation(mn.kotlin.reflect)
    // suspend resolvers (#185): the library starts a coroutine only when this is on the classpath
    implementation(mn.kotlinx.coroutines.core)
    // Flow subscriptions (#181): the library adapts a Flow only when this is on the classpath
    implementation(mn.kotlinx.coroutines.reactive)
    ksp(mn.micronaut.inject.kotlin)
    kspTest(mn.micronaut.inject.kotlin)
    // the compile-time checks (#291): the processor registers the visitor, which KSP loads next to Micronaut's processor
    ksp(projects.micronautGraphqlToolsProcessor)
    runtimeOnly(mnLogging.logback.classic)
    testImplementation(mn.micronaut.http.client)
    // The graphql-binding management endpoint (#515) is served only with Micronaut Management on the classpath. Test scope: the application itself does not depend on it.
    testImplementation(mn.micronaut.management)
    testImplementation(projects.testSuiteTck) // the execution-behaviour TCK (#53)
    // The metrics and tracing modules and the libraries their tests observe with: the Micrometer and OpenTelemetry integrations of
    // Micronaut, and an in-memory span exporter (#24). Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsMicrometer)
    testImplementation(projects.micronautGraphqlToolsOpentelemetry)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    // the mapper of @Timed, which KSP gives to the test source set; it is not transitive because its dependencies include a second
    // Micronaut processor, and it needs Micrometer on the processor path
    ksp(mnMicrometer.micronaut.micrometer.annotation) { isTransitive = false }
    ksp(mnMicrometer.micrometer.core)
    // newer KSP releases no longer give kspTest the processors of ksp, and the test source set holds the @Timed resolver
    kspTest(mnMicrometer.micronaut.micrometer.annotation) { isTransitive = false }
    kspTest(mnMicrometer.micrometer.core)
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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("example.Application")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

// The Micronaut BOM pins kotlin-stdlib and kotlin-reflect to the Kotlin it was built with. Code compiled by a newer Kotlin needs the matching runtime library
// (its coroutine debug metadata and its helper classes), so the runtime follows the compiler plugin, which keeps the canary on the newest Kotlin honest.
configurations.matching { !it.name.startsWith("ksp") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && (requested.name.startsWith("kotlin-stdlib") || requested.name == "kotlin-reflect")) {
            useVersion(org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion(logger))
            because("the runtime library follows the Kotlin compiler plugin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        javaParameters.set(true)
    }
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
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "25"
}

// The directory that the checks read the schema from. KSP does not track the schema files by itself: they are an input of its tasks,
// so that a schema edit reruns them. `module.dir` is the project directory: a compilation that forgot the options in the same JVM
// fails instead of reading these.
val graphqlSchemaDirs = files("src/main/resources")
// The checks cover `main` only: the test fixtures (for example the precedence classes) have no schema file in the schema directories.
// KSP gives `kspTest` the processors of `ksp` (releases before 2.3), so the processor is excluded from it.
configurations.matching { it.name == "kspTest" }.configureEach {
    val processor = project(projects.micronautGraphqlToolsProcessor.path)
    exclude(group = processor.group.toString(), module = processor.name)
}
// The directory of the KSP output of the test source set, which IntrospectionEntriesTest checks (#566).
tasks.withType<Test>().configureEach {
    systemProperty("ksp.test.output", layout.buildDirectory.dir("generated/ksp/test").get().asFile.absolutePath)
}
ksp {
    arg("micronaut.graphql.tools.processor.schema.dirs", graphqlSchemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    arg("micronaut.graphql.tools.processor.module.dir", layout.projectDirectory.asFile.absolutePath)
}
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
    inputs.files(graphqlSchemaDirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE)
}

// The resolver fixtures of the constraint-violations scenario (#468) and the @GraphQLModels fixtures of #528, compiled with
// Micronaut Validation's processor and the graphql-tools processor of `main` in a source set of their own: Micronaut Validation's
// processor re-annotates every class that has @Introspected through a meta-annotation, and the graphql-tools processor puts the
// classes of @GraphQLModels back. The output is on the test classpath. The validation artifacts are not in the `mn` catalog (the
// platform BOM manages them), so no version here.
val validationFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(validationFixtures.implementationConfigurationName) { extendsFrom(implementation.get()) }
}
dependencies {
    "validationFixturesImplementation"(mnValidation.micronaut.validation)
    "kspValidationFixtures"(mn.micronaut.inject.kotlin)
    "kspValidationFixtures"(mnValidation.micronaut.validation.processor)
    "kspValidationFixtures"(projects.micronautGraphqlToolsProcessor)
    testImplementation(validationFixtures.output)
    testImplementation(mnValidation.micronaut.validation)
}
// The resolvers of this source set are completed by a schema customizer, so the schema checks are off here.
tasks.matching { it.name == "kspValidationFixturesKotlin" }.configureEach {
    (this as com.google.devtools.ksp.gradle.KspAATask).commandLineArgumentProviders.add(
        CommandLineArgumentProvider { listOf("micronaut.graphql.tools.processor.checks=false") })
}

// The Micronaut Serialization stack: the library's GraphQL client needs Micronaut Serialization, and micronaut-serde-jackson makes its
// JsonMapper win over the Jackson databind of this application, so the two stacks cannot share a classpath (test-suite-serde has the
// same rule). The tests that call the application with the client, and the declarative clients with their result models, are in the
// source set `serdeTest`, which has its own task; `test` keeps the Jackson databind stack. KSP checks each operation of a declarative
// client against the schema of the application (the reserved client schema `server`, documents in src/serdeTest/graphql/client/server)
// and compiles it.
val serdeTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(serdeTest.implementationConfigurationName) { extendsFrom(implementation.get()) }
}
dependencies {
    "serdeTestImplementation"(projects.micronautGraphqlToolsClient)
    "serdeTestImplementation"(mnSerde.micronaut.serde.jackson)
    "serdeTestImplementation"(mn.micronaut.http.client)
    "serdeTestImplementation"(mn.micronaut.retry)
    "serdeTestImplementation"(mnTest.micronaut.test.junit5)
    "kspSerdeTest"(mn.micronaut.inject.kotlin)
    "kspSerdeTest"(mnSerde.micronaut.serde.processor)
    "kspSerdeTest"(projects.micronautGraphqlToolsProcessor)
    "serdeTestRuntimeOnly"(mnLogging.logback.classic)
    "serdeTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "serdeTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
val graphqlClientDirs = files("src/serdeTest/graphql/client")
tasks.matching { it.name == "kspSerdeTestKotlin" }.configureEach {
    inputs.files(graphqlClientDirs).withPropertyName("graphqlClientDirs").withPathSensitivity(PathSensitivity.RELATIVE)
    (this as com.google.devtools.ksp.gradle.KspAATask).commandLineArgumentProviders.add(CommandLineArgumentProvider {
        listOf("micronaut.graphql.tools.processor.client.dirs=" + graphqlClientDirs.files.joinToString(File.pathSeparator) { it.absolutePath },
            "micronaut.graphql.tools.processor.client.checks=true")
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
// The same tests on the JDK HTTP client: the Netty HTTP client is removed from the class path, because both registries are primary and
// the Netty client wins whenever it is present. HttpClientImplementationTest asserts the implementation that the task selects.
val serdeTestJdkRuntime = configurations.create("serdeTestJdkRuntimeClasspath") {
    isCanBeConsumed = false
    extendsFrom(configurations.getByName(serdeTest.implementationConfigurationName), configurations.getByName(serdeTest.runtimeOnlyConfigurationName))
    exclude(group = "io.micronaut", module = "micronaut-http-client")
    attributes {
        configurations.getByName(serdeTest.runtimeClasspathConfigurationName).attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, configurations.getByName(serdeTest.runtimeClasspathConfigurationName).attributes.getAttribute(key)!!)
        }
    }
}
dependencies {
    "serdeTestJdkRuntimeClasspath"(mn.micronaut.http.client.jdk)
}
val serdeTestJdkTask = tasks.register<Test>("serdeTestJdk") {
    description = "Runs the tests of serdeTest on the JDK HTTP client instead of the Netty HTTP client."
    group = "verification"
    testClassesDirs = serdeTest.output.classesDirs
    classpath = files(serdeTest.output, sourceSets.main.get().output, serdeTestJdkRuntime)
    systemProperty("graphql.test.http-client", "jdk")
    useJUnitPlatform()
    shouldRunAfter(serdeTestTask)
}
tasks.named("check") { dependsOn(serdeTestJdkTask) }

// A class that drifts from the schema on purpose, compiled with the processor and the options of `main`: driftFixtureTest asserts
// that the compilation fails with the expected `[code] coordinate` line.
val driftFixture: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(driftFixture.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named("kspDriftFixture") { extendsFrom(configurations.getByName("ksp")) }
}
extra["driftCompileTask"] = "compileDriftFixtureKotlin"
extra["driftFailingTask"] = "kspDriftFixtureKotlin"
// the processor classpath of the fixture is an input of its KSP task, which Gradle builds next to the source set
extra["driftInputs"] = files(fileTree("src/driftFixture"), driftFixture.compileClasspath,
    provider { tasks.getByName("kspDriftFixtureKotlin").inputs.files }, graphqlSchemaDirs)
apply(from = rootProject.file("config/drift-fixture.gradle.kts"))

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
