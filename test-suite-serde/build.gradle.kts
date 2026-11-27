plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.application")
}

repositories {
    mavenCentral()
}

// The Micronaut Serialization stack of the library (#45, #285): micronaut-serde-jackson makes its JsonMapper win over Jackson databind, so
// this suite is a module of its own, apart from test-suite-java, which runs the Jackson databind stack. It serves a small schema and checks over
// raw HTTP that a response keeps its null fields.
dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation(mnSerde.micronaut.serde.jackson)
    annotationProcessor(projects.micronautGraphqlToolsProcessor)
    // The client, tested against the server of this application (#685). Test scope: the application itself does not use it.
    testImplementation(projects.micronautGraphqlToolsClient)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.reactor)
    annotationProcessor(mnSerde.micronaut.serde.processor)
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    // The client visitor writes the compiled operations of the declarative clients of the tests (#687)
    testAnnotationProcessor(projects.micronautGraphqlToolsProcessor)
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
graalvmNative.binaries.configureEach { buildArgs.add("-H:+SharedArenaSupport") }

micronaut {
    version.set(libs.versions.micronaut.platform)
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("example.*")
    }
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
        "-Amicronaut.graphql.tools.processor.schema.dirs=" + schemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath },
        "-Amicronaut.graphql.tools.processor.client.dirs=" + clientDirs.files.joinToString(File.pathSeparator) { it.absolutePath },
        "-Amicronaut.graphql.tools.processor.module.dir=" + moduleDir.get().asFile.absolutePath)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLClientDirs>().apply {
        schemaDirs.from(files("src/main/resources"))
        clientDirs.from(files("src/test/graphql/client"))
        moduleDir.set(layout.projectDirectory)
    })
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDirs>().apply {
        dirs.from(files("src/main/resources"))
        moduleDir.set(layout.projectDirectory)
    })
}
