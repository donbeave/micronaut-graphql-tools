plugins {
    id("io.micronaut.application") version "4.6.2"
    id("com.gradleup.shadow") version "8.3.9"
    id("io.micronaut.aot") version "4.6.2"
    id("com.apollographql.apollo").version("4.4.1")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation(libs.apollo.runtime)
    implementation(libs.apollo.rx3.support)
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

micronaut {
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
    jdkVersion = "21"
}

apollo {
    service("service") {
        packageName.set("example.client")
        schemaFiles.from("src/main/resources/schema.graphqls")
        generateKotlinModels.set(false)
    }
}

// The compile-time checks of the library, applied to this application as an application would apply them.
dependencies {
    annotationProcessor(projects.micronautGraphqlToolsProcessor)
}

// The schema directory, declared as an input of the compilation: a schema change recompiles. Only its *.graphqls
// and *.graphql files are read (src/main/graphql holds client operations, not the schema).
abstract class GraphQLSchemaDir : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dir: DirectoryProperty

    override fun asArguments() = listOf("-Amicronaut.graphql.tools.sdlDir=${dir.get().asFile.absolutePath}")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDir>().apply {
        dir.set(layout.projectDirectory.dir("src/main/resources"))
    })
}

// The guard: the build fails if the checks did not run (a processor that is not applied runs silently not at all).
val checkBindingManifest by tasks.registering {
    val manifest = tasks.named<JavaCompile>("compileJava")
            .flatMap { it.destinationDirectory.file("META-INF/micronaut-graphql-tools/binding-manifest.txt") }
    inputs.files(tasks.named("compileJava"))
    doLast {
        if (!manifest.get().asFile.isFile) {
            throw GradleException("The GraphQL compile-time checks did not run: ${manifest.get().asFile} is missing.")
        }
    }
}
tasks.named("check") { dependsOn(checkBindingManifest) }
