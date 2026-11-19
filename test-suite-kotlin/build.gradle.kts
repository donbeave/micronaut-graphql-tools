plugins {
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
    // The JSON codec of the application. The library no longer brings Jackson (#36), so the default stack declares it here.
    runtimeOnly(mn.micronaut.jackson.databind)
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    ksp("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    // the compile-time checks (#291): the processor registers the visitor, which KSP loads next to Micronaut's processor
    ksp(projects.micronautGraphqlToolsProcessor)
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(projects.graphqlToolsTck) // the execution-behaviour TCK (#53)
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
ksp {
    arg("micronaut.graphql.tools.processor.schema.dirs", graphqlSchemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    arg("micronaut.graphql.tools.processor.module.dir", layout.projectDirectory.asFile.absolutePath)
}
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
    inputs.files(graphqlSchemaDirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE)
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
apply(from = rootProject.file("config/spotless.license.gradle"))
