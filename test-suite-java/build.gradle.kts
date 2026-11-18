plugins {
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
    implementation(libs.apollo.java.client)
    implementation(libs.apollo.java.rx3)
    implementation(libs.apollo.api) // the Java client 1.0.0 resolves apollo-api 5.0.1; keep the API at the plugin's version
    testImplementation(projects.graphqlToolsTck) // the execution-behaviour TCK (#53)
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

dependencies {
    annotationProcessor(projects.micronautGraphqlToolsProcessor)
}

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

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x (D-34).
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
    inputs.files(reference, *copies.toTypedArray())
    doLast {
        copies.forEach { copy ->
            if (copy.asFile.readText() != reference.asFile.readText()) {
                throw GradleException("${copy.asFile} differs from ${reference.asFile}: the three test suites serve one schema.")
            }
        }
    }
}
tasks.named("check") { dependsOn(checkSuiteSchemaParity) }

// The Micronaut Serialization stack (D-03) has its own test source set and task: micronaut-serde-jackson makes its JsonMapper
// win over Jackson databind, so the two stacks cannot share one test classpath. `check` runs it; its tests are raw-HTTP
// checks that a response keeps its null fields (#45), and the same queries run on the default stack in `test`, which
// `nativeTest` also runs. This suite is JVM-only: see CONTRIBUTING.md.
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
}
val serdeTestTask = tasks.register<Test>("serdeTest") {
    description = "Runs the tests on Micronaut Serialization (micronaut-serde-jackson)."
    group = "verification"
    testClassesDirs = serdeTest.output.classesDirs
    classpath = serdeTest.runtimeClasspath
    useJUnitPlatform()
}
tasks.named("check") { dependsOn(serdeTestTask) }
