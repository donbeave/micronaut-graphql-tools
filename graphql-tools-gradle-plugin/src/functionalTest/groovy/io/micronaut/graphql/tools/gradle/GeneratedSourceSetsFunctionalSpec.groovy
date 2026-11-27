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
package io.micronaut.graphql.tools.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.nio.file.Files
import java.util.stream.Collectors

/**
 * The generated sources reach the compilers of a Java, a Kotlin (KSP and kapt) and a Groovy project: the language compile task
 * alone runs the generation first, the compilers see the generated enum, Micronaut's processors introspect a generated
 * {@code @Introspected} class, and {@code sourcesJar} carries the generated files. The generator emits no model class yet, so the
 * scenarios add one to the output of the generation task, as the emitter will.
 */
class GeneratedSourceSetsFunctionalSpec extends PluginFunctionalSpec {

    private static final String PLATFORM = System.getProperty('plugin.micronaut-platform')
    private static final String SCHEMA_WITH_ENUM = SCHEMA + '\nenum Genre {\n  FANTASY\n}\n'
    private static final String SEED = '''
        tasks.named("generateGraphQL") {
            val output = layout.buildDirectory.dir("generated/sources/graphqlTools/main")
            doLast {
                val file = output.get().file("example/Seeded.java").asFile
                file.parentFile.mkdirs()
                file.writeText(
                    "package example;\\n" +
                    "@io.micronaut.core.annotation.Introspected\\n" +
                    "public class Seeded {\\n" +
                    "    private String name;\\n" +
                    "    public String getName() { return name; }\\n" +
                    "    public void setName(String name) { this.name = name; }\\n" +
                    "}\\n")
            }
        }
        java { withSourcesJar() }
    '''.stripIndent()

    private String platform(String configuration) {
        "$configuration(platform(\"io.micronaut.platform:micronaut-platform:$PLATFORM\"))"
    }

    private void javaProject() {
        settings()
        schema(SCHEMA_WITH_ENUM)
        write('build.gradle.kts', kotlinBuild() + SEED + """
            dependencies {
                ${platform('implementation')}
                ${platform('annotationProcessor')}
                implementation("io.micronaut:micronaut-inject")
                annotationProcessor("io.micronaut:micronaut-inject-java")
            }
        """.stripIndent())
        write('src/main/java/example/App.java', '''package example;

public class App {
    public static Object[] values() {
        return new Object[] {example.model.Genre.FANTASY, new Seeded()};
    }
}
''')
    }

    private void kotlinProject(String processorPlugin, String processorDependencies) {
        settings()
        schema(SCHEMA_WITH_ENUM)
        write('build.gradle.kts', """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${System.getProperty('plugin.kotlin')}"
                $processorPlugin
                id("io.micronaut.graphql.tools")
            }
            repositories {
                maven { url = uri("${repository()}") }
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                codegen { packageName = "example" }
            }
            java {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            kotlin {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
            }
            dependencies {
                ${platform('implementation')}
                implementation("io.micronaut:micronaut-inject")
                $processorDependencies
            }
        """.stripIndent() + SEED)
        write('src/main/kotlin/example/App.kt', '''package example

object App {
    fun values(): List<Any> = listOf(example.model.Genre.FANTASY, Seeded())
}
''')
    }

    private void groovyProject() {
        settings()
        schema(SCHEMA_WITH_ENUM)
        write('build.gradle.kts', kotlinBuild('codegen { packageName = "example" }', 'groovy') + SEED + """
            dependencies {
                ${platform('implementation')}
                ${platform('annotationProcessor')}
                ${platform('compileOnly')}
                implementation("io.micronaut:micronaut-inject")
                implementation("org.apache.groovy:groovy:${System.getProperty('plugin.groovy')}")
                annotationProcessor("io.micronaut:micronaut-inject-java")
                compileOnly("io.micronaut:micronaut-inject-groovy")
            }
        """.stripIndent())
        write('src/main/groovy/example/App.groovy', '''package example

class App {
    static List values() { [example.model.Genre.FANTASY, new Seeded()] }
}
''')
    }

    private boolean introspected() {
        Files.walk(project.resolve('build')).withCloseable { paths ->
            paths.map { it.fileName.toString() }.collect(Collectors.toList())
        }.contains('$Seeded$Introspection.class')
    }

    private static boolean ranBefore(BuildResult result, String first, String second) {
        def paths = result.tasks*.path
        paths.contains(first) && paths.contains(second) && paths.indexOf(first) < paths.indexOf(second)
    }

    private List<String> sourcesJarEntries() {
        def jar = Files.list(project.resolve('build/libs')).withCloseable { it.collect(Collectors.toList()) }
                .find { it.fileName.toString().endsWith('-sources.jar') }
        new java.util.zip.ZipFile(jar.toFile()).withCloseable { zip -> zip.entries().collect { it.name } }
    }

    @Unroll
    def "compileJava alone generates first, and Micronaut introspects the generated class on Gradle #gradle"() {
        given:
        javaProject()

        when:
        def result = build(gradle, 'compileJava')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        result.task(':compileJava').outcome == TaskOutcome.SUCCESS
        ranBefore(result, ':generateGraphQL', ':compileJava')
        introspected()

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "sourcesJar alone generates first and carries the generated files on Gradle #gradle"() {
        given:
        javaProject()

        when:
        def result = build(gradle, 'sourcesJar')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        ranBefore(result, ':generateGraphQL', ':sourcesJar')
        sourcesJarEntries().containsAll(['example/model/Genre.java', 'example/Seeded.java'])

        where:
        gradle << GRADLE_VERSIONS
    }

    def "kspKotlin alone generates first, and the build compiles the Kotlin source against the generated Java"() {
        given:
        kotlinProject('id("com.google.devtools.ksp") version "' + System.getProperty('plugin.ksp') + '"',
                platform('ksp') + '\nksp("io.micronaut:micronaut-inject-kotlin")')

        when:
        def first = build(CURRENT, 'kspKotlin')
        def second = build(CURRENT, 'build')

        then:
        ranBefore(first, ':generateGraphQL', ':kspKotlin')
        second.task(':compileKotlin').outcome == TaskOutcome.SUCCESS
        second.task(':compileJava').outcome == TaskOutcome.SUCCESS
        introspected()
    }

    def "kaptGenerateStubsKotlin alone generates first, and the build compiles the Kotlin source against the generated Java"() {
        given:
        kotlinProject('id("org.jetbrains.kotlin.kapt") version "' + System.getProperty('plugin.kotlin') + '"',
                platform('kapt') + '\nkapt("io.micronaut:micronaut-inject-java")')

        when:
        def first = build(CURRENT, 'kaptGenerateStubsKotlin')
        def second = build(CURRENT, 'build')

        then:
        ranBefore(first, ':generateGraphQL', ':kaptGenerateStubsKotlin')
        second.task(':compileKotlin').outcome == TaskOutcome.SUCCESS
        introspected()
    }

    @Unroll
    def "compileGroovy alone generates first, and the Groovy source compiles against the generated Java on Gradle #gradle"() {
        given:
        groovyProject()

        when:
        def result = build(gradle, 'compileGroovy')

        then:
        ranBefore(result, ':generateGraphQL', ':compileJava')
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        introspected()

        where:
        gradle << GRADLE_VERSIONS
    }
}
