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

/**
 * The Kotlin backend of the client corpus: one generated Gradle build with one subproject per Kotlin case, compiled by Gradle
 * TestKit with the production toolchain (the Kotlin Gradle plugin and KSP2 of the test suites, {@code micronaut-inject-kotlin} as the
 * KSP processor, or kapt with {@code -Pcorpus.kotlin.processor=kapt}), in one {@code --continue --parallel} run. The visitors are on the
 * processor path with the client directory and the options of the case; a finding is an {@code e: [ksp]} line of the processing task, as for
 * the resolver cases ({@link KotlinBatchCompiler}, whose toolchain pins, class path and message parsing this class uses). A case that
 * compiles is started afterwards by {@link ClientVerdicts} to read the metadata that the visitor wrote.
 */
@CompileStatic
final class KotlinClientBatch implements ClientCaseCompiler {

    private final ClientCorpus corpus
    private final Path buildDir
    private final KotlinBatchCompiler.Processor processor
    private final String only
    private BuildResult result
    private Map<String, List<String>> output
    /** wall time of the Gradle build, in milliseconds, or -1 before it ran */
    long buildMillis = -1

    KotlinClientBatch(ClientCorpus corpus, Path buildDir, KotlinBatchCompiler.Processor processor = KotlinBatchCompiler.Processor.KSP,
                      String only = CorpusFixtures.only()) {
        this.corpus = corpus
        this.buildDir = buildDir
        this.processor = processor
        this.only = only
    }

    @Override
    Language getLanguage() {
        return Language.KOTLIN
    }

    /** The cases this backend builds: every case with Kotlin sources under KSP, the {@code lang:all} cases under kapt (the nightly run). */
    static boolean covers(KotlinBatchCompiler.Processor processor, ClientCorpusCase corpusCase) {
        return corpusCase.languages().contains(Language.KOTLIN) && (processor == KotlinBatchCompiler.Processor.KSP || corpusCase.hasTag('lang:all'))
    }

    static String projectName(ClientCorpusCase corpusCase) {
        return corpusCase.name.replace('/', '-')
    }

    /**
     * Builds the Gradle projects of all the cases, once. The first case does it otherwise, and the build (294 s of a CI runner for 90
     * cases) would count as the run time of that one test against the limit of 5 minutes that stops a hung test.
     */
    synchronized void prepare() {
        if (result == null) {
            build()
        }
    }

    @Override
    synchronized ClientCompiled compile(ClientCorpusCase corpusCase, Path work) {
        if (result == null) {
            build()
        }
        String project = projectName(corpusCase)
        Path root = buildDir.resolve(project)
        Path clientDir = Files.isDirectory(root.resolve('client')) ? root.resolve('client') : (corpusCase.clientDirs == 'missing' ? root.resolve('missing-client') : null)
        Path schemaDir = Files.isDirectory(root.resolve('schema')) ? root.resolve('schema') : null
        boolean ok = result.task(":${project}:compileKotlin")?.outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE]
        List<String> errors = KotlinBatchCompiler.messages(output.getOrDefault(project, []), 'e')
        if (!ok && errors.empty) {
            errors = ['compileKotlin did not run or failed: ' + result.output.readLines().takeRight(20).join('\n')]
        }
        List<Path> generated = ['build/classes/kotlin/main', 'build/classes/java/main', 'build/resources/main', 'build/generated/ksp/main/classes',
                                'build/generated/ksp/main/resources', 'build/tmp/kapt3/classes/main'].collect { root.resolve(it) }.findAll { Files.isDirectory(it) }
        return new ClientCompiled(ok, errors, generated, clientDir, schemaDir)
    }

    private void build() {
        buildDir.toFile().deleteDir()
        Files.createDirectories(buildDir)
        List<ClientCorpusCase> cases = corpus.cases.findAll { covers(processor, it) && (only == null || it.name ==~ only) }
        Set<String> seen = new HashSet<>()
        cases.collect { projectName(it) }.findAll { !seen.add(it) }.each {
            throw new IllegalStateException("two Kotlin client cases map to the Gradle project '${it}': rename one of them".toString())
        }
        Files.writeString(buildDir.resolve('settings.gradle.kts'), """\
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "corpus-kotlin-client"
${cases.collect { "include(\"${projectName(it)}\")" }.join('\n')}
""")
        Files.writeString(buildDir.resolve('build.gradle.kts'), """\
plugins {
    id("org.jetbrains.kotlin.jvm") version "${KotlinBatchCompiler.KOTLIN}" apply false
    id("com.google.devtools.ksp") version "${KotlinBatchCompiler.KSP}" apply false
    id("org.jetbrains.kotlin.kapt") version "${KotlinBatchCompiler.KOTLIN}" apply false
}
""")
        Files.writeString(buildDir.resolve('gradle.properties'), 'kapt.workers.isolation=process\norg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g\n')
        Path registration = Files.createDirectories(buildDir.resolve('visitor-registration/META-INF/services'))
        Files.writeString(registration.resolve(KotlinBatchCompiler.VISITOR_SERVICE.substring('META-INF/services/'.length())),
                'io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor\nio.micronaut.graphql.tools.visitor.client.GraphQLClientVisitor\n')
        List<String> visitorPath = KotlinBatchCompiler.visitorPath().collect { it.replace('\\', '/') } + [buildDir.resolve('visitor-registration').toString().replace('\\', '/')]
        String classpath = KotlinBatchCompiler.libraries().collect { "        \"${it.replace('\\', '/')}\"," }.join('\n')
        String path = visitorPath.collect { "        \"${it}\"," }.join('\n')
        cases.each { writeProject(it, classpath, path) }
        long start = System.nanoTime()
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(buildDir.toFile())
                .withArguments(['--continue', '--parallel', '--console=plain', '--stacktrace'] + cases.collect { ":${projectName(it)}:compileKotlin".toString() })
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
        output = KotlinBatchCompiler.attribute(result.output.readLines())
    }

    private void writeProject(ClientCorpusCase c, String classpath, String path) {
        String name = projectName(c)
        Path project = Files.createDirectories(buildDir.resolve(name))
        Path sources = Files.createDirectories(project.resolve('src/main/kotlin'))
        c.sources(Language.KOTLIN).each { Path file ->
            Path target = sources.resolve(c.dir.resolve(Language.KOTLIN.dir).relativize(file).toString())
            Files.createDirectories(target.parent)
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        }
        ClientCaseCompiler.Setup setup = new ClientCaseCompiler.Setup(c, project)
        String micronaut = System.getProperty('corpus.micronaut.platform', '4.10.25')
        // every option in every compile: under kapt, javac copies an option into the system properties of the compiler JVM
        Map<String, String> values = new LinkedHashMap<>(setup.options)
        List<String> arguments = values.collect { String key, String value -> "arg(\"${key}\", \"${value.replace('\\', '/')}\")".toString() }
        String options = processor == KotlinBatchCompiler.Processor.KSP ? "ksp {\n    ${arguments.join('\n    ')}\n}" : """\
kapt {
    arguments {
        ${arguments.join('\n        ')}
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    kaptProcessJvmArgs.add("-Dcorpus.kapt.worker=${name}")
}"""
        Files.writeString(project.resolve('build.gradle.kts'), """\
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("${processor.plugin}")
}

repositories {
    mavenCentral()
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

dependencies {
    implementation(files(
${classpath}
    ))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    ${processor.configuration}(platform("io.micronaut.platform:micronaut-platform:${micronaut}"))
    ${processor.configuration}("${processor.micronaut}")
    ${processor.configuration}(files(
${path}
    ))
}

${options}
""")
    }

}
