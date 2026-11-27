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

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class GenerateTaskFunctionalSpec extends PluginFunctionalSpec {

    @Unroll
    def "the extension DSL works in a Kotlin build script on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild())

        when:
        def result = build(gradle, 'generateGraphQL')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        exists('build/generated/sources/graphqlTools/main')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the extension DSL works in a Groovy build script on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle', """
            plugins {
                id 'java'
                id 'io.micronaut.graphql.tools'
            }
            repositories {
                maven { url = uri('${new File(System.getProperty('plugin.repository')).toURI()}') }
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                codegen {
                    packageName = 'example'
                    typeMappings = [DateTime: 'java.time.Instant']
                    interfaceTypes = ['Node']
                }
                codegen('other') {
                    packageName = 'example.other'
                }
            }
        """.stripIndent())

        when:
        def result = build(gradle, 'generateGraphQL', 'generateOtherGraphQL')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        result.task(':generateOtherGraphQL').outcome == TaskOutcome.SUCCESS
        exists('build/generated/sources/graphqlTools/other')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the generator is neither on the build-script classpath nor on the application's classpaths on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild() + '''
            tasks.register("report") {
                val compile = configurations.named("compileClasspath")
                val runtime = configurations.named("runtimeClasspath")
                val generator = configurations.named("graphqlToolsCodegenClasspath")
                doLast {
                    val onBuildScript = try {
                        Class.forName("io.micronaut.graphql.tools.codegen.Options", false, javaClass.classLoader)
                        true
                    } catch (e: ClassNotFoundException) {
                        false
                    }
                    println("BUILD_SCRIPT=" + onBuildScript)
                    println("COMPILE=" + compile.get().files.map { it.name })
                    println("RUNTIME=" + runtime.get().files.map { it.name })
                    println("GENERATOR=" + generator.get().files.map { it.name })
                }
            }
        ''')

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains('BUILD_SCRIPT=false')
        output.contains('COMPILE=[]')
        output.contains('RUNTIME=[]')
        output =~ /GENERATOR=\[.*micronaut-graphql-tools-codegen-.*graphql-java-.*\]/

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "an invalid option fails the task with the diagnostic of the generator on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild('codegen { language = "COBOL" }'))

        when:
        def result = buildAndFail(gradle, 'generateGraphQL')

        then:
        result.output.contains('[invalid-option-value]')
        result.output.contains('language')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the default version of the generator is the version of the plugin on Gradle #gradle"() {
        given:
        settings()
        write('build.gradle.kts', kotlinBuild() + '''
            tasks.register("report") {
                val version = graphqlTools.version
                val declared = configurations.named("graphqlToolsCodegen")
                doLast {
                    println("VERSION=" + version.get())
                    println("DECLARED=" + declared.get().dependencies.map { "${it.group}:${it.name}:${it.version}" })
                }
            }
        ''')

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains("VERSION=${VERSION}")
        output.contains("DECLARED=[io.micronaut.graphql.tools:micronaut-graphql-tools-codegen:${VERSION}]")

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the runtime dependency is added by default and not when it is off on Gradle #gradle"() {
        given:
        settings()
        write('build.gradle.kts', """
            plugins {
                java
                id("io.micronaut.graphql.tools")
            }
            graphqlTools {
                codegen("client") { mode = "CLIENT" }
            }
            tasks.register("report") {
                val implementation = configurations.named("implementation")
                doLast { println("DECLARED=" + implementation.get().dependencies.map { "\${it.name}:\${it.version}" }.sorted()) }
            }
        """.stripIndent())

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains("DECLARED=[micronaut-graphql-tools-client:${VERSION}, micronaut-graphql-tools:${VERSION}]")

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "an unchanged build is up to date, an edit reruns the task, and a clean build comes from the cache on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild())

        expect: 'the first run generates'
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.SUCCESS

        and: 'the second run is up to date'
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.UP_TO_DATE

        and: 'a schema edit reruns the task'
        schema(SCHEMA + '\ntype Author {\n  name: String!\n}\n')
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.SUCCESS

        and: 'an option change reruns the task'
        write('build.gradle.kts', kotlinBuild('codegen { packageName = "example.changed" }'))
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.SUCCESS

        and: 'a clean build restores the output from the build cache'
        build(gradle, 'clean', 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.FROM_CACHE

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the task recreates its output directory, so a removed file leaves nothing stale on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild())
        build(gradle, 'generateGraphQL')
        write('build/generated/sources/graphqlTools/main/example/model/Removed.java', 'class Removed {}')

        when:
        schema(SCHEMA + '\ntype Author {\n  name: String!\n}\n')
        build(gradle, 'generateGraphQL')

        then:
        !exists('build/generated/sources/graphqlTools/main/example/model/Removed.java')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "a spec with no schema file fails with a message that names the schema directory setting on Gradle #gradle"() {
        given:
        settings()
        write('build.gradle.kts', kotlinBuild())

        when:
        def result = buildAndFail(gradle, 'generateGraphQL')

        then:
        result.output.contains('graphqlTools.schemaDirs')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "includes and excludes narrow the schema files on Gradle #gradle"() {
        given:
        settings()
        schema()
        schema('this is not graphql', 'src/main/resources/graphql/internal/broken.graphqls')
        write('build.gradle.kts', kotlinBuild('codegen { packageName = "example"; excludes = listOf("internal/**") }'))

        when:
        def result = build(gradle, 'generateGraphQL')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "a schema file that is not GraphQL fails the task with its location on Gradle #gradle"() {
        given:
        settings()
        schema('type Query {')
        write('build.gradle.kts', kotlinBuild())

        when:
        def result = buildAndFail(gradle, 'generateGraphQL')

        then:
        result.output.contains('[schema-syntax-error]')
        result.output.contains('schema.graphqls')

        where:
        gradle << GRADLE_VERSIONS
    }
}
