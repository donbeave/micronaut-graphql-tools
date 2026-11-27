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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class GraphQLToolsPluginSpec extends Specification {

    def "the default version of the generator is the version of the plugin"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply('io.micronaut.graphql.tools')

        then:
        project.extensions.getByType(GraphQLToolsExtension).version.get() == System.getProperty('plugin.version')
        project.configurations.graphqlToolsCodegen.dependencies.collect { "${it.group}:${it.name}:${it.version}" } ==
                ["io.micronaut.graphql.tools:micronaut-graphql-tools-codegen:${System.getProperty('plugin.version')}"]
    }

    def "the generator configurations are a bucket and a resolvable classpath"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply('io.micronaut.graphql.tools')

        then:
        !project.configurations.graphqlToolsCodegen.canBeResolved
        project.configurations.graphqlToolsCodegenClasspath.canBeResolved
        project.configurations.graphqlToolsCodegenClasspath.extendsFrom == [project.configurations.graphqlToolsCodegen] as Set
    }

    def "codegen configures the main spec and a name creates another spec with its own task"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('io.micronaut.graphql.tools')
        def extension = project.extensions.getByType(GraphQLToolsExtension)

        when:
        extension.codegen { it.packageName.set('example') }
        extension.codegen('client') { it.mode.set('CLIENT') }

        then:
        extension.codegenSpecs.names == ['main', 'client'] as SortedSet
        extension.codegenSpecs.getByName('main').packageName.get() == 'example'
        project.tasks.findByName('generateGraphQL') instanceof GenerateGraphQLTask
        project.tasks.findByName('generateClientGraphQL') instanceof GenerateGraphQLTask
        project.tasks.getByName('generateClientGraphQL').outputDirectory.get().asFile.path.endsWith('generated/sources/graphqlTools/client')
    }

    def "the language follows the applied Groovy plugin, else it is Java"() {
        given:
        def java = ProjectBuilder.builder().build()
        def groovy = ProjectBuilder.builder().build()

        when:
        java.pluginManager.apply('io.micronaut.graphql.tools')
        groovy.pluginManager.apply('groovy')
        groovy.pluginManager.apply('io.micronaut.graphql.tools')

        then:
        java.extensions.getByType(GraphQLToolsExtension).codegenSpecs.getByName('main').language.get() == 'JAVA'
        groovy.extensions.getByType(GraphQLToolsExtension).codegenSpecs.getByName('main').language.get() == 'GROOVY'
    }
}
