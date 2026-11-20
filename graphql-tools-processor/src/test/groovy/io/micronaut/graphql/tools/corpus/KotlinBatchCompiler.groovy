/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Matcher

/**
 * The Kotlin backend: one generated Gradle build with two subprojects per Kotlin case, compiled by Gradle TestKit
 * with the production toolchain (the Kotlin Gradle plugin and KSP2 of the versions the test suites use, Micronaut's
 * {@code micronaut-inject-kotlin} as the KSP processor), in one {@code --continue --parallel} run. {@code KotlinCompiler}
 * of micronaut-test is not used: it pins Kotlin 1.9 and KSP1, fails on JDK 25 and cannot pass KSP arguments.
 *
 * <p>The first call builds every case; each later call reads the outcome of its own subprojects. The unchecked subproject builds
 * what the {@link RuntimeBinder} starts: the visitor is on the processor path with {@code checks=false}, as a Java case compiles.
 * The checked subproject runs {@code kspKotlin} only, with the visitor and the schema of the case, and the
 * {@code e: [ksp] [code] coordinate} lines of that task are what the {@link VisitorCompileTimeBinder} parses. With
 * {@link Processor#KAPT} the same build applies kapt in place of KSP (the nightly run of the {@code lang:all} cases).</p>
 *
 * <p>The visitor is registered for the build in a directory of its own, not by the test resources of this module (which also
 * register a test visitor): the processor path is the module's classes, the binding module and graphql-java.</p>
 *
 * <p>The build needs the network (plugin portal, Maven Central) unless the Gradle user home it shares with the outer
 * build already holds the plugins and the processor.</p>
 */
@CompileStatic
final class KotlinBatchCompiler implements CheckedCaseCompiler {

    /** versions of the production toolchain: the test suites' Kotlin and KSP2, see {@code test-suite-kotlin} */
    static final String KOTLIN = System.getProperty('corpus.kotlin.version', '2.1.21')
    static final String KSP = System.getProperty('corpus.ksp.version', '2.1.21-2.0.2')
    static final String VISITOR_SERVICE = 'META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor'

    /** The annotation processing the build applies: what the case is judged under. */
    enum Processor {

        KSP('ksp', 'kspKotlin', 'ksp', 'com.google.devtools.ksp', 'io.micronaut:micronaut-inject-kotlin'),
        KAPT('kapt', 'kaptKotlin', 'kapt', 'org.jetbrains.kotlin.kapt', 'io.micronaut:micronaut-inject-java')

        final String configuration
        final String task
        final String label
        final String plugin
        final String micronaut

        Processor(String configuration, String task, String label, String plugin, String micronaut) {
            this.configuration = configuration
            this.task = task
            this.label = label
            this.plugin = plugin
            this.micronaut = micronaut
        }

        static Processor fromProperty() {
            return valueOf(System.getProperty('corpus.kotlin.processor', 'ksp').toUpperCase())
        }

    }

    private final Corpus corpus
    private final Path buildDir
    private final Processor processor
    private BuildResult result
    private Map<String, List<String>> output
    private Map<String, String> failures
    /** wall time of the Gradle build, in milliseconds, or -1 before it ran */
    long buildMillis = -1

    KotlinBatchCompiler(Corpus corpus, Path buildDir, Processor processor = Processor.KSP) {
        this.corpus = corpus
        this.buildDir = buildDir
        this.processor = processor
    }

    @Override
    Language getLanguage() {
        return Language.KOTLIN
    }

    /** The cases this backend builds: every case with Kotlin sources under KSP, the {@code lang:all} cases under kapt (the nightly run). */
    static boolean covers(Processor processor, CorpusCase corpusCase) {
        return corpusCase.languages().contains(Language.KOTLIN) && (processor == Processor.KSP || corpusCase.hasTag('lang:all'))
    }

    static String projectName(CorpusCase corpusCase) {
        return corpusCase.name.replace('/', '-')
    }

    static String checkedName(CorpusCase corpusCase) {
        return projectName(corpusCase) + '--checked'
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
        List<Path> generated = ['build/classes/java/main', 'build/resources/main', 'build/generated/ksp/main/classes',
                                'build/generated/ksp/main/resources', 'build/tmp/kapt3/classes/main'].collect { root.resolve(it) }.findAll { Files.isDirectory(it) }
        return new CompiledCase(true, root.resolve('build/classes/kotlin/main'), [], generated)
    }

    /**
     * The messages of the visitor are the errors and warnings that the processing task of the checked subproject printed: each
     * starts with {@code e: [ksp]} or {@code w: [ksp]} (KSP) or is a javac diagnostic on a stub (kapt), and runs on over the
     * lines that follow it, up to the next message.
     */
    @Override
    synchronized CompiledCase compileChecked(CorpusCase corpusCase, Path work) {
        if (result == null) {
            build()
        }
        String project = checkedName(corpusCase)
        Path root = buildDir.resolve(project)
        List<String> lines = output.getOrDefault(project, [])
        List<String> errors = messages(lines, 'e')
        List<String> warnings = messages(lines, 'w')
        TaskOutcome outcome = result.task(":${project}:${processor.task}")?.outcome
        boolean success = outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE]
        if (!success && errors.empty) {
            errors = [failures.getOrDefault(project, processor.task + ' did not run or failed')]
        }
        return new CompiledCase(success, root, errors, [], warnings)
    }

    /** Groups the lines of a task into messages: a line with the prefix {@code <kind>: } starts one, an indented line continues it. */
    private List<String> messages(List<String> lines, String kind) {
        List<String> result = []
        StringBuilder current = null
        lines.each { String line ->
            Matcher start = (line =~ /^(?:([ewi]): (?:\[ksp\] )?|\S*\.java:\d+: (error|warning): |(error|warning): )(.*)$/)
            if (start.find()) {
                if (current != null) {
                    result.add(current.toString())
                    current = null
                }
                String tag = start.group(1) ?: (start.group(2) ?: start.group(3))
                if (tag == kind || (kind == 'e' && tag == 'error') || (kind == 'w' && tag == 'warning')) {
                    current = new StringBuilder(start.group(4))
                }
            } else if (current != null && line =~ /^\s+\S/) {
                current.append('\n').append(line)
            } else if (current != null) {
                result.add(current.toString())
                current = null
            }
        }
        if (current != null) {
            result.add(current.toString())
        }
        return result
    }

    /** Writes the build, runs it once for every Kotlin case, and records the outcome. */
    private void build() {
        buildDir.toFile().deleteDir()
        Files.createDirectories(buildDir)
        List<CorpusCase> cases = corpus.cases.findAll { covers(processor, it) }
        List<String> projects = cases.collectMany { [projectName(it), checkedName(it)] }
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
    id("org.jetbrains.kotlin.kapt") version "${KOTLIN}" apply false
}
""")
        // kapt: one JVM per compilation, so that the options of one case cannot stay in the system properties of the next (javac
        // copies a micronaut option there and the first value stays)
        // the build runs every case in one daemon, whose default heap of 512 MiB runs out at about 80 cases
        Files.writeString(buildDir.resolve('gradle.properties'),
                'kapt.workers.isolation=process\norg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g\n')
        Path registration = Files.createDirectories(buildDir.resolve('visitor-registration/META-INF/services'))
        Files.writeString(registration.resolve('io.micronaut.inject.visitor.TypeElementVisitor'), 'io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor\n')
        List<String> visitorPath = visitorPath().collect { it.replace('\\', '/') } + [buildDir.resolve('visitor-registration').toString().replace('\\', '/')]
        String classpath = libraries().collect { "        \"${it.replace('\\', '/')}\"," }.join('\n')
        String path = visitorPath.collect { "        \"${it}\"," }.join('\n')
        cases.each { CorpusCase c ->
            writeProject(c, projectName(c), false, classpath, path)
            writeProject(c, checkedName(c), true, classpath, path)
        }
        long start = System.nanoTime()
        List<String> tasks = cases.collectMany { [":${projectName(it)}:compileKotlin".toString(), ":${checkedName(it)}:${processor.task}".toString()] }
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(buildDir.toFile())
                .withArguments(['--continue', '--parallel', '--console=plain', '--stacktrace'] + tasks)
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
        List<String> lines = result.output.readLines()
        output = attribute(lines)
        failures = [:]
        projects.each { String project ->
            List<String> own = output.getOrDefault(project, [])
            List<String> errors = own.findAll { it.startsWith('e: ') }
            int failed = lines.findIndexOf { it.startsWith("Execution failed for task ':${project}:") }
            if (errors.empty && failed >= 0) {
                errors = lines.subList(failed, Math.min(lines.size(), failed + 6))
            }
            if (!errors.empty) {
                failures.put(project, errors.join('\n'))
            }
        }
    }

    /**
     * The plain console prints {@code > Task :<project>:<task>} when the output of another task starts, so the lines after a
     * header belong to its project, in a parallel build as well.
     */
    private static Map<String, List<String>> attribute(List<String> lines) {
        Map<String, List<String>> byProject = [:]
        String project = null
        lines.each { String line ->
            Matcher header = (line =~ /^> Task :([^:\s]+):\S+/)
            if (header.find()) {
                project = header.group(1)
            } else if (line =~ /^(FAILURE|BUILD|\* Try|\* What went wrong|\* Exception)/) {
                project = null
            } else if (project != null) {
                byProject.computeIfAbsent(project) { new ArrayList<String>() }.add(line)
            }
        }
        return byProject
    }

    private void writeProject(CorpusCase c, String name, boolean checked, String classpath, String path) {
        Path project = Files.createDirectories(buildDir.resolve(name))
        Path sources = Files.createDirectories(project.resolve('src/main/kotlin'))
        c.sources(Language.KOTLIN).each { Path file ->
            Path target = sources.resolve(c.dir.resolve(Language.KOTLIN.dir).relativize(file).toString())
            Files.createDirectories(target.parent)
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        }
        String micronaut = System.getProperty('corpus.micronaut.platform', '4.10.24')
        // the unchecked build has the checks off and the schema of the case; the checked build has what the case asks for (CompileOptions).
        // Both options in every compile: under kapt, javac copies an option into the system properties of the compiler JVM and a
        // leftover value of another compilation can win over the argument
        Map<String, String> values = checked ? CompileOptions.of(c, project) : [(CompileOptions.SCHEMA_DIRS): CompileOptions.of(c, project).get(CompileOptions.SCHEMA_DIRS)]
        values.putIfAbsent(CompileOptions.CHECKS, String.valueOf(checked))
        String prefixes = c.config[JavaCaseCompiler.ACCESSOR_PREFIXES_CONFIG]
        if (checked && prefixes != null) {
            values.put(JavaCaseCompiler.ACCESSOR_PREFIXES, prefixes)
        }
        List<String> arguments = values.findAll { it.value != null }.collect { String key, String value ->
            "arg(\"${key}\", \"${value.replace('\\', '/')}\")".toString()
        }
        String options = processor == Processor.KSP ? "ksp {\n    ${arguments.join('\n    ')}\n}" : "kapt {\n    arguments {\n        ${arguments.join('\n        ')}\n    }\n}"
        Files.writeString(project.resolve('build.gradle.kts'), """\
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("${processor.plugin}")
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
    // the version that the Micronaut platform manages, for the cases that call kotlinx.coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    ${processor.configuration}(platform("io.micronaut.platform:micronaut-platform:${micronaut}"))
    ${processor.configuration}("${processor.micronaut}")
    ${processor.configuration}(files(
${path}
    ))
}

${options}
""")
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

    /** The visitor and what it needs besides the processor API: this module's classes, the binding module and graphql-java. */
    private static List<String> visitorPath() {
        return System.getProperty('java.class.path').split(File.pathSeparator).findAll {
            String name = new File(it).name
            (it.contains('graphql-tools-processor/build/classes/java/main')) ||
                    it.contains('graphql-tools-binding/build/') || name.startsWith('micronaut-graphql-tools-binding') ||
                    ['graphql-java-2', 'java-dataloader', 'reactive-streams', 'slf4j-api'].any { prefix -> name.startsWith(prefix) }
        }.toList()
    }

}
