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
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    annotationProcessor(projects.micronautGraphqlToolsProcessor)
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    testAnnotationProcessor("io.micronaut.serde:micronaut-serde-processor")
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

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDirs>().apply {
        dirs.from(files("src/main/resources"))
        moduleDir.set(layout.projectDirectory)
    })
}
