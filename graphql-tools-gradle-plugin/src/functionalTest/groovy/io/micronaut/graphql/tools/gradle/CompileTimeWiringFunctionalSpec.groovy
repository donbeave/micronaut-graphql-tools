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

import spock.lang.Unroll

/**
 * The compile-time wiring: graphqlTools.schemaDirs reaches the processor for javac, the Groovy compiler, KSP and kapt, and only
 * for a compiler that has the processor declared. The processor is declared but never resolved: the scenarios read the options.
 */
class CompileTimeWiringFunctionalSpec extends PluginFunctionalSpec {

    private static final String PROCESSOR = 'io.micronaut.graphql.tools:micronaut-graphql-tools-processor:0'

    @Unroll
    def "javac gets the schema directory and the module directory when the processor is declared on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild() + """
            dependencies {
                annotationProcessor("$PROCESSOR")
            }
            tasks.register("report") {
                val compile = tasks.named<JavaCompile>("compileJava")
                doLast {
                    compile.get().options.compilerArgumentProviders.forEach { println("ARGS=" + it.asArguments().toList()) }
                }
            }
        """.stripIndent())

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains("-Amicronaut.graphql.tools.processor.schema.dirs=${project.toRealPath()}/src/main/resources/graphql")
        output.contains("-Amicronaut.graphql.tools.processor.module.dir=${project.toRealPath()}")

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "javac gets no option when the processor is not declared on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild() + '''
            tasks.register("report") {
                val compile = tasks.named<JavaCompile>("compileJava")
                doLast { println("PROVIDERS=" + compile.get().options.compilerArgumentProviders.size) }
            }
        ''')

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains('PROVIDERS=0')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the Groovy compiler gets the schema directory as a system property of its JVM on Gradle #gradle"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', kotlinBuild('codegen { packageName = "example" }', 'groovy') + """
            dependencies {
                compileOnly("$PROCESSOR")
            }
            tasks.register("report") {
                val compile = tasks.named<GroovyCompile>("compileGroovy")
                doLast { println("JVMARGS=" + compile.get().options.forkOptions.jvmArgs) }
            }
        """.stripIndent())

        when:
        def output = build(gradle, 'report').output

        then:
        output.contains("-Dmicronaut.graphql.tools.processor.schema.dirs=${project.toRealPath()}/src/main/resources/graphql")

        where:
        gradle << GRADLE_VERSIONS
    }

    def "KSP gets the schema directory as an argument when the processor is declared"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${System.getProperty('plugin.kotlin')}"
                id("com.google.devtools.ksp") version "${System.getProperty('plugin.ksp')}"
                id("io.micronaut.graphql.tools")
            }
            repositories { mavenCentral() }
            graphqlTools { addRuntimeDependency = false }
            dependencies { ksp("$PROCESSOR") }
            tasks.register("report") {
                val ksp = project.extensions.getByName("ksp") as com.google.devtools.ksp.gradle.KspExtension
                doLast { println("KSP=" + ksp.arguments) }
            }
        """.stripIndent())

        when:
        def output = build(CURRENT, 'report').output

        then:
        output.contains("micronaut.graphql.tools.processor.schema.dirs=${project.toRealPath()}/src/main/resources/graphql")
    }

    def "kapt gets the schema directory as an argument when the processor is declared"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${System.getProperty('plugin.kotlin')}"
                id("org.jetbrains.kotlin.kapt") version "${System.getProperty('plugin.kotlin')}"
                id("io.micronaut.graphql.tools")
            }
            repositories { mavenCentral() }
            graphqlTools { addRuntimeDependency = false }
            dependencies { kapt("$PROCESSOR") }
            tasks.register("report") {
                val kapt = project.extensions.getByName("kapt") as org.jetbrains.kotlin.gradle.plugin.KaptExtension
                doLast { println("KAPT=" + kapt.getAdditionalArguments(project, null, null)) }
            }
        """.stripIndent())

        when:
        def output = build(CURRENT, 'report').output

        then:
        output.contains("micronaut.graphql.tools.processor.schema.dirs=${project.toRealPath()}/src/main/resources/graphql")
    }

    def "the Kotlin plugin makes Kotlin the language and the output a Kotlin source directory"() {
        given:
        settings()
        schema()
        write('build.gradle.kts', """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${System.getProperty('plugin.kotlin')}"
                id("io.micronaut.graphql.tools")
            }
            repositories { mavenCentral() }
            graphqlTools { addRuntimeDependency = false }
            tasks.register("report") {
                val language = graphqlTools.codegenSpecs.getByName("main").language
                val kotlinDirs = kotlin.sourceSets.getByName("main").kotlin.srcDirs
                doLast {
                    println("LANGUAGE=" + language.get())
                    println("KOTLIN_DIRS=" + kotlinDirs.map { it.path })
                }
            }
        """.stripIndent())

        when:
        def output = build(CURRENT, 'report').output

        then:
        output.contains('LANGUAGE=KOTLIN')
        output.contains('build/generated/sources/graphqlTools/main')
    }
}
