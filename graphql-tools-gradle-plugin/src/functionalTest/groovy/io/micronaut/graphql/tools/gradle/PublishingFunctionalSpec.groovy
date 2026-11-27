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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

import java.nio.file.Path

/**
 * The plugin as a user receives it, without the TestKit plugin class path: from a repository, from a Maven Local repository that the
 * task publishToMavenLocal of this repository filled, and from an included build of this repository that also supplies the generator.
 */
class PublishingFunctionalSpec extends PluginFunctionalSpec {

    private static final String REPOSITORY_ROOT = System.getProperty('plugin.repository-root').replace('\\', '/')

    private static final String GENRE = '\nenum Genre {\n  FANTASY\n}\n'

    @TempDir
    Path mavenLocal

    private static final String PLUGIN_REPORT = '''
        tasks.register("report") {
            val generator = configurations.named("graphqlToolsCodegenClasspath")
            val plugin = io.micronaut.graphql.tools.gradle.GraphQLToolsPlugin::class.java.protectionDomain.codeSource.location
            doLast {
                println("PLUGIN=" + plugin)
                println("GENERATOR=" + generator.get().files.map { it.absolutePath })
            }
        }
    '''

    def "the plugin id resolves from a repository through its marker and so does the generator"() {
        given:
        def repository = new File(System.getProperty('plugin.repository')).toURI()
        write('settings.gradle.kts', """
            pluginManagement { repositories { maven { url = uri("${repository}") }; mavenCentral() } }
            rootProject.name = "sample"
        """.stripIndent())
        write('gradle.properties', "org.gradle.vfs.watch=false\n")
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', """
            plugins {
                java
                id("io.micronaut.graphql.tools") version "${VERSION}"
            }
            repositories {
                maven { url = uri("${repository}") }
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                codegen { packageName = "example" }
            }
        """.stripIndent())

        when:
        def generated = runner(CURRENT, 'generateGraphQL').build()

        then:
        generated.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')
    }

    def "the plugin id resolves from Maven Local and so does the generator"() {
        given:
        publishToMavenLocal()
        write('settings.gradle.kts', """
            pluginManagement { repositories { mavenLocal(); mavenCentral() } }
            rootProject.name = "sample"
        """.stripIndent())
        write('gradle.properties', "org.gradle.vfs.watch=false\n")
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', """
            plugins {
                java
                id("io.micronaut.graphql.tools") version "${VERSION}"
            }
            repositories {
                mavenLocal()
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                codegen { packageName = "example" }
            }
        """.stripIndent() + PLUGIN_REPORT)

        when:
        def generated = runner(CURRENT, "-Dmaven.repo.local=${mavenLocal}", 'generateGraphQL', 'report').build()

        then:
        generated.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')
        generated.output.contains("PLUGIN=file:${mavenLocal}/io/micronaut/graphql/tools/micronaut-graphql-tools-gradle-plugin/${VERSION}/")
        generated.output.contains("GENERATOR=[${mavenLocal}/io/micronaut/graphql/tools/micronaut-graphql-tools-codegen/${VERSION}/")
    }

    def "the plugin id and the generator resolve from an included build"() {
        given:
        write('settings.gradle.kts', """
            pluginManagement { includeBuild("${REPOSITORY_ROOT}") }
            includeBuild("${REPOSITORY_ROOT}")
            rootProject.name = "sample"
        """.stripIndent())
        write('gradle.properties', "org.gradle.vfs.watch=false\n")
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', """
            plugins {
                java
                id("io.micronaut.graphql.tools")
            }
            repositories {
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                codegen { packageName = "example" }
            }
        """.stripIndent() + PLUGIN_REPORT)

        when:
        def generated = runner(CURRENT, 'generateGraphQL', 'report').build()

        then:
        generated.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')
        !generated.output.contains('/files-2.1/io.micronaut.graphql.tools/')
        generated.output =~ /GENERATOR=\[${REPOSITORY_ROOT}\/graphql-tools-codegen\/build\/libs\/micronaut-graphql-tools-codegen-/
    }

    protected GradleRunner runner(String gradle, String... arguments) {
        GradleRunner.create()
                .withGradleVersion(gradle)
                .withProjectDir(project.toFile())
                .withTestKitDir(new File(System.getProperty('plugin.gradle-user-home')))
                .withArguments(['--stacktrace', '--warning-mode=none'] + arguments.toList())
    }

    private void publishToMavenLocal() {
        GradleRunner.create()
                .withGradleVersion(CURRENT)
                .withProjectDir(new File(REPOSITORY_ROOT))
                .withTestKitDir(new File(System.getProperty('plugin.gradle-user-home')))
                .withArguments('--stacktrace', '--warning-mode=none', "-Dmaven.repo.local=${mavenLocal}",
                        ':micronaut-graphql-tools-codegen:publishToMavenLocal', ':micronaut-graphql-tools-gradle-plugin:publishToMavenLocal')
                .build()
    }
}
