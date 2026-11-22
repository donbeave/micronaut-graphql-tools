plugins {
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    groovy
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    // The standard scalars: java.time, JSON and the extended scalars (#46)
    implementation(projects.micronautGraphqlToolsScalars)
    // the compile-time checks (#291): the Groovy compiler loads the visitor from its compile classpath
    compileOnly(projects.micronautGraphqlToolsProcessor)
    // The JSON codec of the application. The library no longer brings Jackson (#36), so the default stack declares it here.
    runtimeOnly(mn.micronaut.jackson.databind)
    // The Groovy runtime of the application classes. Nothing else puts it on the runtime classpath: the tests got it from
    // Spock, so the application itself (run, shadowJar) was missing it.
    implementation("org.apache.groovy:groovy")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(projects.graphqlToolsTck) // the execution-behaviour TCK (#53)
    // The metrics and tracing modules and the libraries their tests observe with: the Micrometer and OpenTelemetry integrations of
    // Micronaut, and an in-memory span exporter (#24). Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsMicrometer)
    testImplementation(projects.micronautGraphqlToolsOpentelemetry)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mnTracing.micronaut.tracing.opentelemetry)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation(mnMicrometer.micronaut.micrometer.annotation)
    // The upload module: the Upload scalar and the multipart route, tested with a real multipart client (#47). Test scope: the application
    // itself does not depend on it.
    testImplementation(projects.micronautGraphqlToolsUpload)
    // The security module and Micronaut Security: the principal in the context, @Secured on resolvers and the root fields hook, tested with
    // a real HTTP and WebSocket client. Test scope: the application itself does not depend on them.
    testImplementation(projects.micronautGraphqlToolsSecurity)
    testImplementation(mnSecurity.micronaut.security)
}

graalvmNative.toolchainDetection = false

application {
    mainClass.set("example.Application")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    // The platform version: micronaut-build reads `micronaut` as the core BOM version, which has no platform of the same number.
    version.set(libs.versions.micronaut.platform)
    runtime("netty")
    testRuntime("spock2")
    processing {
        incremental(true)
        annotations("example.*")
    }
}

// The resolver fixtures of the constraint-violations scenario (#468) and the @GraphQLModels fixtures of #528, compiled with
// Micronaut Validation's processor and the graphql-tools processor of `main` in a source set of their own: Micronaut Validation's
// processor re-annotates every class that has @Introspected through a meta-annotation, and the graphql-tools processor puts the
// classes of @GraphQLModels back. The output is on the test classpath. The validation artifacts are not in the `mn` catalog (the
// platform BOM manages them), so no version here. The resolvers of this source set are completed by a schema customizer, so the
// schema checks are off here.
val validationFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(validationFixtures.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(validationFixtures.compileOnlyConfigurationName) { extendsFrom(configurations.testCompileOnly.get()) }
}
dependencies {
    "validationFixturesImplementation"("io.micronaut.validation:micronaut-validation")
    "validationFixturesCompileOnly"("io.micronaut.validation:micronaut-validation-processor")
    "validationFixturesCompileOnly"(projects.micronautGraphqlToolsProcessor)
    testImplementation(validationFixtures.output)
    testImplementation("io.micronaut.validation:micronaut-validation")
}
tasks.named<GroovyCompile>(validationFixtures.getCompileTaskName("groovy")) {
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.checks=false")
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

// The directory that the checks read the schema from. The Groovy compiler reads its options from system properties, so it runs in
// its own JVM (the default). Gradle does not track the schema files by itself: they are an input of the task, so that a schema edit
// recompiles. `module.dir` is the project directory: a compilation that forgot the options in the same JVM fails instead of reading these.
val graphqlSchemaDirs = files("src/main/resources")
fun GroovyCompile.checkSchema() {
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.schema.dirs=" +
        graphqlSchemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.module.dir=" + layout.projectDirectory.asFile.absolutePath)
    inputs.files(graphqlSchemaDirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE)
}
tasks.named<GroovyCompile>("compileGroovy") { checkSchema() }

// A class that drifts from the schema on purpose, compiled with the processor and the options of `main`: driftFixtureTest asserts
// that the compilation fails with the expected `[code] coordinate` line.
val driftFixture: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(driftFixture.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(driftFixture.compileOnlyConfigurationName) { extendsFrom(compileOnly.get()) }
}
tasks.named<GroovyCompile>(driftFixture.getCompileTaskName("groovy")) { checkSchema() }
extra["driftCompileTask"] = driftFixture.getCompileTaskName("groovy")
extra["driftFailingTask"] = driftFixture.getCompileTaskName("groovy")
extra["driftInputs"] = files(fileTree("src/driftFixture"), driftFixture.compileClasspath, graphqlSchemaDirs)
apply(from = rootProject.file("config/drift-fixture.gradle.kts"))

// invokedynamic call sites (Groovy's default) fail to bootstrap in a native image (IndyInterface.make throws a NullPointerException).
tasks.named<GroovyCompile>("compileGroovy") {
    groovyOptions.optimizationOptions = (groovyOptions.optimizationOptions ?: mutableMapOf()).apply { put("indy", false) }
}

// Spock cannot run natively, so the native executable of the application is smoke-tested instead (#276): the script
// starts it, posts the queries of the Java and Kotlin smoke tests and asserts the response bodies. Not part of `check`:
// a native build takes minutes and about 5 GB.
val nativeSmoke: Configuration by configurations.creating
dependencies {
    nativeSmoke("org.apache.groovy:groovy:${libs.versions.groovy.get()}")
    nativeSmoke("org.apache.groovy:groovy-json:${libs.versions.groovy.get()}")
}
tasks.register<JavaExec>("nativeSmokeTest") {
    description = "Builds the native executable, starts it and asserts GraphQL responses (Groovy has no native test)."
    group = "verification"
    val executable = layout.buildDirectory.file("native/nativeCompile/${project.name}")
    val script = layout.projectDirectory.file("src/nativeSmoke/groovy/NativeSmoke.groovy")
    dependsOn("nativeCompile")
    inputs.file(script)
    classpath = nativeSmoke
    mainClass.set("groovy.ui.GroovyMain")
    argumentProviders.add(CommandLineArgumentProvider { listOf(script.asFile.absolutePath, executable.get().asFile.absolutePath) })
    outputs.upToDateWhen { false }
}

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
apply(from = rootProject.file("config/spotless.license.gradle"))
