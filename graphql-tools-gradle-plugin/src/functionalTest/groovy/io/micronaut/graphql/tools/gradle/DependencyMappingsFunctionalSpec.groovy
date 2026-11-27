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

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The type mappings file of a dependency: the plugin gives the generator the jars of the project's dependencies, so a type that a
 * dependency maps needs no typeMappings entry, and it writes the mappings file of the module into a resource directory of the main
 * source set.
 */
class DependencyMappingsFunctionalSpec extends PluginFunctionalSpec {

    static final String MAPPINGS = 'META-INF/micronaut-graphql-tools/type-mappings.properties'
    static final String SCHEMA_WITH_MAPPED_TYPES = 'type Query {\n  pet: Pet\n  price: Money\n}\nscalar Money\n'
    static final String RESOURCE = 'build/generated/resources/graphqlTools/main/' + MAPPINGS

    private void jar(String path, String mappings) {
        def file = project.resolve(path)
        Files.createDirectories(file.parent)
        new ZipOutputStream(Files.newOutputStream(file)).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry(MAPPINGS))
            zip.write(mappings.getBytes('UTF-8'))
            zip.closeEntry()
        }
    }

    private String buildScript(String extension = 'codegen { packageName = "example" }') {
        kotlinBuild(extension) + '\ndependencies { implementation(files("libs/types.jar")) }\n'
    }

    @Unroll
    def "a type that a dependency jar maps needs no typeMappings entry, and the module's own file is a resource on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA_WITH_MAPPED_TYPES)
        jar('libs/types.jar', 'Pet=org.lib.Pet\nMoney=org.lib.Money\n')
        write('build.gradle.kts', buildScript())

        when:
        def result = build(gradle, 'processResources')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        Files.readString(project.resolve(RESOURCE)).contains('Money=org.lib.Money')
        exists('build/resources/main/' + MAPPINGS)

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "a change in a dependency's mappings reruns the task and an unchanged build is up to date on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA_WITH_MAPPED_TYPES)
        jar('libs/types.jar', 'Pet=org.lib.Pet\nMoney=org.lib.Money\n')
        write('build.gradle.kts', buildScript())

        expect:
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.UP_TO_DATE

        when:
        jar('libs/types.jar', 'Pet=org.lib.Pet\nMoney=org.other.Money\n')
        def result = build(gradle, 'generateGraphQL')

        then:
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        Files.readString(project.resolve(RESOURCE)).contains('Money=org.other.Money')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "with typeMappingsFromDependencies off the dependencies are not read on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA_WITH_MAPPED_TYPES)
        jar('libs/types.jar', 'Pet=org.lib.Pet\nMoney=org.lib.Money\n')
        write('build.gradle.kts', buildScript('codegen { packageName = "example"; typeMappingsFromDependencies = false }'))

        when:
        def result = buildAndFail(gradle, 'generateGraphQL')

        then:
        result.output.contains('[unknown-type]')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "the mappings file of a project dependency is read on Gradle #gradle"() {
        given:
        write('settings.gradle.kts', """
            rootProject.name = "sample"
            include("lib")
        """.stripIndent())
        write('gradle.properties', 'org.gradle.vfs.watch=false\n')
        write('lib/build.gradle.kts', 'plugins { `java-library` }\n')
        write('lib/src/main/resources/' + MAPPINGS, 'Pet=org.lib.Pet\nMoney=org.lib.Money\n')
        schema(SCHEMA_WITH_MAPPED_TYPES)
        write('build.gradle.kts', kotlinBuild() + '\ndependencies { implementation(project(":lib")) }\n')

        when:
        def result = build(gradle, 'generateGraphQL')

        then:
        result.task(':lib:jar').outcome == TaskOutcome.SUCCESS
        result.task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        Files.readString(project.resolve(RESOURCE)).contains('Money=org.lib.Money')

        where:
        gradle << GRADLE_VERSIONS
    }
}
