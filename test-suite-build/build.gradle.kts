plugins {
    java
}

// The build wiring of the compile-time checks (#290): sample projects that apply the processor the way a consumer does, built by
// Gradle TestKit and Maven against the library published to the repository of this build. Nothing here is published.
//   samples/     the projects; their build files carry the tagged snippets that the guide includes
//   src/test     the specs that build the samples and assert the outcomes
// The specs run in their own task, buildToolsTest, which `check` does not run: the CI job `build tools` runs it once.

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

dependencies {
    testImplementation(platform(libs.micronaut.platform))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val libraryModules = listOf(":micronaut-graphql-tools", ":micronaut-graphql-tools-binding", ":micronaut-graphql-tools-processor",
    ":micronaut-graphql-tools-bom")

// The library as a consumer resolves it: the four modules, published to the build repository of the root project.
val publishLibrary by tasks.registering {
    description = "Publishes the library modules to build/repo."
    libraryModules.forEach { dependsOn("$it:publishAllPublicationsToBuildRepository") }
}

// The samples resolve the library from this repository, with the registration that the published processor jar has: the visitor and only the visitor.
val sampleRepository = rootProject.layout.buildDirectory.dir("repo")

// The plugin versions of the samples are the ones of the root build, so that a bump (or the Kotlin canary's rewrite) moves them.
fun pluginPin(id: String): String =
    Regex("""id\("${Regex.escape(id)}"\) version "([^"]+)"""")
        .find(rootProject.file("build.gradle.kts").readText())?.groupValues?.get(1)
        ?: throw GradleException("The root build.gradle.kts has no version for the plugin $id")

val samples = layout.projectDirectory.dir("samples")
val buildToolsTest by tasks.registering(Test::class) {
    description = "Builds the sample projects with Gradle TestKit and Maven and asserts the outcomes."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    // the CI log shows each spec and the outcomes that the specs record
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    dependsOn(publishLibrary)
    doFirst {
        delete(layout.buildDirectory.dir("reports/build-tools"))
    }
    inputs.files(samples).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("samples")
    inputs.dir(sampleRepository).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sampleRepository")
    systemProperty("sample.dir", samples.asFile.absolutePath)
    systemProperty("sample.repository", sampleRepository.get().asFile.absolutePath)
    systemProperty("sample.work", layout.buildDirectory.dir("sample-work").get().asFile.absolutePath)
    systemProperty("sample.report", layout.buildDirectory.dir("reports/build-tools").get().asFile.absolutePath)
    systemProperty("sample.gradle-user-home", gradle.gradleUserHomeDir.absolutePath)
    systemProperty("sample.graphql-tools.version", providers.gradleProperty("projectVersion").get())
    systemProperty("sample.micronaut.platform", libs.versions.micronaut.platform.get())
    systemProperty("sample.micronaut-gradle-plugin", pluginPin("io.micronaut.application"))
    systemProperty("sample.kotlin", pluginPin("org.jetbrains.kotlin.jvm"))
    systemProperty("sample.ksp", pluginPin("com.google.devtools.ksp"))
    systemProperty("sample.groovy", libs.versions.groovy.get())
    outputs.upToDateWhen { false }
}
// The compile time of the checks (#418): gradle-profiler scenarios (profiler/compile-time.scenarios) run against copies of the samples,
// and a budget (profiler/compile-time-budget.properties) that the measured times must stay under.
//   compileTimeProfile  needs gradle-profiler on the PATH (or -PgradleProfiler=<path>) and the network; -Pprofiler.warmups and
//                       -Pprofiler.iterations set the number of builds of each scenario
//   compileTimeBudget   fails when a measured median exceeds its budget; writes build/reports/compile-time/report.md
val profilerDir = layout.projectDirectory.dir("profiler")
val profilerProjects = layout.buildDirectory.dir("profiler/projects")
val profilerOutput = layout.buildDirectory.dir("profiler/output")
val profilerWarmups = providers.gradleProperty("profiler.warmups").orElse("2")
val profilerIterations = providers.gradleProperty("profiler.iterations").orElse("5")
val profiledSamples = listOf("java-gradle", "kotlin-ksp", "kotlin-kapt", "groovy-gradle", "multi-module-gradle")

val prepareCompileTimeProfile by tasks.registering(Sync::class) {
    description = "Copies the profiled samples with the properties and the init script that the scenarios need."
    dependsOn(publishLibrary)
    profiledSamples.forEach { from(samples.dir(it)) { into(it) } }
    profiledSamples.forEach { from(profilerDir.file("profiler.init.gradle")) { into(it) } }
    into(profilerProjects)
    doLast {
        profiledSamples.forEach {
            profilerProjects.get().dir(it).file("gradle.properties").asFile.writeText(listOf(
                "graphqlToolsRepository=" + sampleRepository.get().asFile.toURI(),
                "graphqlToolsVersion=" + providers.gradleProperty("projectVersion").get(),
                "micronautPlatformVersion=" + libs.versions.micronaut.platform.get(),
                "micronautGradlePluginVersion=" + pluginPin("io.micronaut.application"),
                "kotlinVersion=" + pluginPin("org.jetbrains.kotlin.jvm"),
                "kspVersion=" + pluginPin("com.google.devtools.ksp"),
                "groovyVersion=" + libs.versions.groovy.get(),
                "org.gradle.vfs.watch=false",
                "org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8",
                "").joinToString("\n"))
        }
    }
}

val compileTimeProfile by tasks.registering(Exec::class) {
    description = "Runs the gradle-profiler scenarios of the compile-time checks."
    group = "verification"
    dependsOn(prepareCompileTimeProfile)
    inputs.dir(profilerDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { false }
    commandLine(profilerDir.file("run.sh").asFile.absolutePath, providers.gradleProperty("gradleProfiler").getOrElse("gradle-profiler"),
        profilerProjects.get().asFile.absolutePath, profilerOutput.get().asFile.absolutePath, profilerWarmups.get(), profilerIterations.get(), gradle.gradleVersion)
}

val compileTimeBudget by tasks.registering(JavaExec::class) {
    description = "Fails when a measured compile time exceeds the budget; writes the per-module report."
    group = "verification"
    dependsOn(compileTimeProfile, tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.micronaut.graphql.tools.buildtools.CompileTimeBudget")
    args(profilerOutput.get().asFile.absolutePath, profilerDir.file("compile-time-budget.properties").asFile.absolutePath,
        layout.buildDirectory.file("reports/compile-time/report.md").get().asFile.absolutePath, profilerIterations.get())
}

// The standard test task stays out of `check`: the specs are slow, build real projects and need the network.
tasks.test {
    enabled = false
}

apply(from = rootProject.file("config/spotless.license.gradle"))
// The sample sources carry the header too; the standard targets cover src/ only.
extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
    format("samples") {
        target("samples/**/src/*/java/**/*.java", "samples/**/src/*/groovy/**/*.groovy", "samples/**/src/*/kotlin/**/*.kt")
        licenseHeaderFile(rootProject.file("config/spotless.license.java"), "package ")
    }
}
