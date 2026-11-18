package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The Kotlin backend: one generated Gradle build with one subproject per Kotlin case, compiled by Gradle TestKit
 * with the production toolchain (the Kotlin Gradle plugin and KSP2 of the versions the test suites use, Micronaut's
 * {@code micronaut-inject-kotlin} as the KSP processor), in one {@code --continue --parallel} run. {@code KotlinCompiler}
 * of micronaut-test is not used: it pins Kotlin 1.9 and KSP1, fails on JDK 25 and cannot pass KSP arguments.
 *
 * <p>The first {@link #compile} builds every case; each later call reads the outcome of its own subproject. The
 * compiled classes are started by the {@link RuntimeBinder} like the in-process ones. The build has one subproject per
 * case, the unchecked one: the checked subproject (the visitor of #150 on the {@code ksp} configuration, whose
 * {@code e: [ksp] [code] coordinate} lines the compile-time binder parses) and the kapt variant (nightly, {@code lang:all}
 * cases) arrive with #150 and #143.</p>
 *
 * <p>The build needs the network (plugin portal, Maven Central) unless the Gradle user home it shares with the outer
 * build already holds the plugins and the processor.</p>
 */
@CompileStatic
final class KotlinBatchCompiler implements CaseCompiler {

    /** versions of the production toolchain: the test suites' Kotlin and KSP2, see {@code test-suite-kotlin} */
    static final String KOTLIN = System.getProperty('corpus.kotlin.version', '2.1.21')
    static final String KSP = System.getProperty('corpus.ksp.version', '2.1.21-2.0.2')

    private final Corpus corpus
    private final Path buildDir
    private BuildResult result
    private Map<String, String> failures
    /** wall time of the Gradle build, in milliseconds, or -1 before it ran */
    long buildMillis = -1

    KotlinBatchCompiler(Corpus corpus, Path buildDir) {
        this.corpus = corpus
        this.buildDir = buildDir
    }

    @Override
    Language getLanguage() {
        return Language.KOTLIN
    }

    static String projectName(CorpusCase corpusCase) {
        return corpusCase.name.replace('/', '-')
    }

    @Override
    synchronized CompiledCase compile(CorpusCase corpusCase, Path work) {
        if (result == null) {
            build()
        }
        String project = projectName(corpusCase)
        Path root = buildDir.resolve(project)
        boolean ok = result.task(":${project}:compileKotlin")?.outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE]
        if (!ok) {
            return new CompiledCase(false, root, [failures.getOrDefault(project, 'compileKotlin did not run or failed: ' + result.output.readLines().takeRight(20).join('\n'))])
        }
        List<Path> output = ['build/classes/java/main', 'build/resources/main', 'build/generated/ksp/main/classes',
                             'build/generated/ksp/main/resources'].collect { root.resolve(it) }.findAll { Files.isDirectory(it) }
        return new CompiledCase(true, root.resolve('build/classes/kotlin/main'), [], output)
    }

    /** Writes the build, runs it once for every Kotlin case, and records the outcome. */
    private void build() {
        buildDir.toFile().deleteDir()
        Files.createDirectories(buildDir)
        List<CorpusCase> cases = corpus.cases.findAll { it.languages().contains(Language.KOTLIN) }
        List<String> projects = cases.collect { projectName(it) }
        // `a-b/c` and `a/b-c` would share the project `a-b-c`: refuse instead of mixing two cases
        Set<String> seen = new HashSet<>()
        projects.findAll { !seen.add(it) }.each {
            throw new IllegalStateException("two Kotlin cases map to the Gradle project '${it}': rename one of them".toString())
        }
        Files.writeString(buildDir.resolve('settings.gradle.kts'), """\
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "corpus-kotlin"
${projects.collect { "include(\"${it}\")" }.join('\n')}
""")
        Files.writeString(buildDir.resolve('build.gradle.kts'), """\
plugins {
    id("org.jetbrains.kotlin.jvm") version "${KOTLIN}" apply false
    id("com.google.devtools.ksp") version "${KSP}" apply false
}
""")
        String micronaut = System.getProperty('corpus.micronaut.platform', '4.10.24')
        String classpath = libraries().collect { "        \"${it.replace('\\', '/')}\"," }.join('\n')
        cases.each { CorpusCase c ->
            Path project = Files.createDirectories(buildDir.resolve(projectName(c)))
            Path sources = Files.createDirectories(project.resolve('src/main/kotlin'))
            c.sources(Language.KOTLIN).each { Path file ->
                Path target = sources.resolve(c.dir.resolve(Language.KOTLIN.dir).relativize(file).toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.writeString(project.resolve('build.gradle.kts'), """\
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
}

repositories {
    mavenCentral()
}

// Java and Kotlin target 21, whatever JDK runs the build (as test-suite-kotlin does)
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

dependencies {
    implementation(files(
${classpath}
    ))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    ksp(platform("io.micronaut.platform:micronaut-platform:${micronaut}"))
    ksp("io.micronaut:micronaut-inject-kotlin")
}
""")
        }
        long start = System.nanoTime()
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(buildDir.toFile())
                .withArguments('--continue', '--parallel', '--console=plain', '--stacktrace', 'compileKotlin')
                .forwardOutput()
        String home = System.getProperty('corpus.gradle-user-home')
        if (home != null) {
            runner.withTestKitDir(new File(home))
        }
        try {
            result = runner.build()
        } catch (UnexpectedBuildFailure e) {
            result = e.buildResult
        }
        buildMillis = (System.nanoTime() - start).intdiv(1_000_000)
        failures = [:]
        projects.each { String project ->
            List<String> lines = result.output.readLines()
            List<String> errors = lines.findAll { it.startsWith('e: ') && it.contains("/${project}/") }
            int failed = lines.findIndexOf { it.startsWith("Execution failed for task ':${project}:") }
            if (errors.empty && failed >= 0) {
                errors = lines.subList(failed, Math.min(lines.size(), failed + 6))
            }
            if (!errors.empty) {
                failures.put(project, errors.join('\n'))
            }
        }
    }

    /** The jars and class directories of the library under test: this test JVM's class path without this module's own output, the Kotlin libraries and the test tooling (Gradle API, Groovy, Spock, JUnit). */
    private static List<String> libraries() {
        return System.getProperty('java.class.path').split(File.pathSeparator).findAll {
            // the Kotlin libraries come from the plugin's own version, never from the test class path (it may hold a newer
            // stdlib than the compiler of the build can read)
            String name = new File(it).name
            !it.contains('graphql-tools-processor/build') && !it.contains('generated-gradle-jars') &&
                    !['kotlin-', 'kotlinx-', 'gradle-', 'groovy', 'spock-', 'junit', 'opentest4j', 'byte-buddy', 'objenesis']
                            .any { prefix -> name.startsWith(prefix) }
        }.toList()
    }

}
