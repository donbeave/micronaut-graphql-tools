import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

// Until the switch (#291) the published processor jar registers the JSR-269 processor and not the visitor. The samples
// need the visitor and only the visitor, as the jar will have it after the switch, so this task copies the repository and
// rewrites the registration in the copy. It changes nothing once the jar already registers the visitor, and #291 deletes it.
abstract class StageSampleRepository : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val target: DirectoryProperty

    @TaskAction
    fun stage() {
        val from = source.get().asFile.toPath()
        val to = target.get().asFile.toPath()
        to.toFile().deleteRecursively()
        Files.walk(from).use { files ->
            files.filter { Files.isRegularFile(it) }.forEach { file ->
                val name = file.fileName.toString()
                if (listOf(".md5", ".sha1", ".sha256", ".sha512").any { name.endsWith(it) }) {
                    return@forEach
                }
                val copy = to.resolve(from.relativize(file).toString())
                Files.createDirectories(copy.parent)
                if (name.startsWith("micronaut-graphql-tools-processor-") && name.endsWith(".jar") &&
                    !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")) {
                    registerVisitor(file, copy)
                } else {
                    Files.copy(file, copy)
                }
            }
        }
    }

    private fun registerVisitor(jar: Path, copy: Path) {
        val jsr269 = setOf("META-INF/services/javax.annotation.processing.Processor", "META-INF/gradle/incremental.annotation.processors")
        val visitorService = "META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor"
        ZipFile(jar.toFile()).use { zip ->
            if (zip.getEntry(visitorService) != null) {
                Files.copy(jar, copy)
                return
            }
            ZipOutputStream(Files.newOutputStream(copy)).use { out ->
                zip.entries().asSequence().filter { it.name !in jsr269 }.forEach { entry ->
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry(visitorService))
                out.write("io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor\n".toByteArray())
                out.closeEntry()
            }
        }
    }
}

val sampleRepository = layout.buildDirectory.dir("sample-repo")
val stageSampleRepository by tasks.registering(StageSampleRepository::class) {
    description = "Copies build/repo for the samples, with the visitor registered in the processor jar."
    dependsOn(publishLibrary)
    source.set(rootProject.layout.buildDirectory.dir("repo"))
    target.set(sampleRepository)
}

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
    dependsOn(stageSampleRepository)
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
// The standard test task stays out of `check`: the specs are slow, build real projects and need the network.
tasks.test {
    enabled = false
}

apply(from = rootProject.file("config/spotless.license.gradle"))
// The sample sources carry the header too (D-44); the standard targets cover src/ only.
extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
    format("samples") {
        target("samples/**/src/*/java/**/*.java", "samples/**/src/*/groovy/**/*.groovy", "samples/**/src/*/kotlin/**/*.kt")
        licenseHeaderFile(rootProject.file("config/spotless.license.java"), "package ")
    }
}
