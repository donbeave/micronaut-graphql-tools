plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.3"
}

// Benchmarks of the request hot path and the allocation gates (D-02). Nothing here is published.
//   src/main                 the benchmarked schema, resolvers and operations, shared by everything below
//   src/jmh                  the JMH benchmarks: ./gradlew :benchmarks:jmh
//   src/jmhSmokeTest         jmhSmokeTest: the JMH benchmarks as a JUnit test (no fork, single shot), part of `check`
//   src/allocationTest       allocationTest: bytes per operation against allocation-baselines.properties, part of `check`

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation(mn.micronaut.inject)
    annotationProcessor(mn.micronaut.inject.java)
    runtimeOnly(libs.logback.classic)

    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

val graphqlJavaVersion = providers.gradleProperty("graphqlJavaVersion").getOrElse("21.3")

val allocationTest by sourceSets.creating
val jmhSmokeTest by sourceSets.creating

configurations {
    named(allocationTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(allocationTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
    named(jmhSmokeTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(jmhSmokeTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
}

dependencies {
    for (testSourceSet in listOf(allocationTest, jmhSmokeTest)) {
        add(testSourceSet.implementationConfigurationName, platform(libs.junit.bom))
        add(testSourceSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
        add(testSourceSet.implementationConfigurationName, sourceSets.main.get().output)
        add(testSourceSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
    add(jmhSmokeTest.implementationConfigurationName, libs.jmh.core)
}

val allocationBaselines = layout.projectDirectory.file("allocation-baselines.properties")

val allocationTestTask = tasks.register<Test>("allocationTest") {
    group = "verification"
    description = "Fails when an operation of the request hot path allocates more than its baseline."
    testClassesDirs = allocationTest.output.classesDirs
    classpath = allocationTest.runtimeClasspath
    useJUnitPlatform()
    // Its own JVM per operation, with a heap that does not grow during a measurement. C1 only: without the escape
    // analysis of C2 the figure is every allocation the code performs, which does not depend on the JIT's choices, the
    // machine or the JDK patch level (C2 figures for one operation differed between two identical runs). JMH's gc
    // profiler reports the C2 steady state.
    forkEvery = 1
    maxParallelForks = 1
    minHeapSize = "512m"
    maxHeapSize = "512m"
    jvmArgs("-XX:+UseG1GC", "-XX:TieredStopAtLevel=1")
    systemProperty("allocation.baselines", allocationBaselines.asFile.absolutePath)
    systemProperty("allocation.graphqlJavaVersion", graphqlJavaVersion)
    systemProperty("allocation.update", providers.gradleProperty("allocation.update").isPresent)
    inputs.file(allocationBaselines).withPropertyName("allocationBaselines")
    outputs.upToDateWhen { false } // a measurement, never up to date
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val jmhSmokeTestTask = tasks.register<Test>("jmhSmokeTest") {
    group = "verification"
    description = "Runs every JMH benchmark once in this JVM, so that they compile and run on every check."
    testClassesDirs = jmhSmokeTest.output.classesDirs
    classpath = jmhSmokeTest.runtimeClasspath + sourceSets["jmh"].output + sourceSets["jmh"].runtimeClasspath +
            files(tasks.named("jmhCompileGeneratedClasses"))
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(allocationTestTask, jmhSmokeTestTask)
}

// -Pjmh.profile=quick is a short run (one fork, a few seconds per benchmark) for a first look. The default profile is the
// measuring run: two forks, five warmup and five measured iterations of one second each.
val quickProfile = providers.gradleProperty("jmh.profile").orNull == "quick"

jmh {
    // The allocation figure is the gc profiler's gc.alloc.rate.norm, in bytes per operation.
    jmhVersion = libs.versions.jmh
    fork = if (quickProfile) 1 else 2
    warmupIterations = if (quickProfile) 2 else 5
    iterations = if (quickProfile) 3 else 5
    warmup = "1s"
    timeOnIteration = "1s"
    profilers = listOf("gc")
    resultFormat = "JSON"
    includes = providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf()).get()
}
