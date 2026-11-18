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

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x (D-34).
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

// Spock cannot run natively (D-38), so the native executable of the application is smoke-tested instead (#276): the script
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
