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
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // suspend resolvers (#185): the library starts a coroutine only when this is on the classpath
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Flow subscriptions (#181): the library adapts a Flow only when this is on the classpath
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    ksp("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    // the compile-time checks (#291): the processor registers the visitor, which KSP loads next to Micronaut's processor
    ksp(projects.micronautGraphqlToolsProcessor)
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut:micronaut-http-client")
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
    ksp("io.micrometer:micrometer-core")
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
// KSP gives `kspTest` the processors of `ksp`, so the processor is excluded from it.
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
    "validationFixturesImplementation"("io.micronaut.validation:micronaut-validation")
    "kspValidationFixtures"("io.micronaut:micronaut-inject-kotlin")
    "kspValidationFixtures"("io.micronaut.validation:micronaut-validation-processor")
    "kspValidationFixtures"(projects.micronautGraphqlToolsProcessor)
    testImplementation(validationFixtures.output)
    testImplementation("io.micronaut.validation:micronaut-validation")
}
// The resolvers of this source set are completed by a schema customizer, so the schema checks are off here.
tasks.matching { it.name == "kspValidationFixturesKotlin" }.configureEach {
    (this as com.google.devtools.ksp.gradle.KspAATask).commandLineArgumentProviders.add(
        CommandLineArgumentProvider { listOf("micronaut.graphql.tools.processor.checks=false") })
}

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
