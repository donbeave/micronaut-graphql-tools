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
    // The JSON codec of the application. The library no longer brings Jackson (#36), so the default stack declares it here.
    runtimeOnly(mn.micronaut.jackson.databind)
    // The Groovy runtime of the application classes. Nothing else puts it on the runtime classpath: the tests got it from
    // Spock, so the application itself (run, shadowJar) was missing it.
    implementation("org.apache.groovy:groovy")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(projects.graphqlToolsTck) // the execution-behaviour TCK (#53)
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

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(libs.versions.groovy.get())
            because("Groovy 4.0.33 reads JDK 27 class files; the BOM's 4.0.28 does not")
        }
    }
}

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

apply(from = rootProject.file("config/spotless.license.gradle"))
