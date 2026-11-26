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
package io.micronaut.graphql.tools.visitor

import spock.lang.Specification

import java.util.zip.ZipFile

/**
 * The registration of the jar (#291): the two visitors, the resolver checks and the client checks, are the compile-time engine of a
 * build, so the jar registers them in one service file and registers nothing else.
 */
class RegistrationSpec extends Specification {

    static final String VISITOR_SERVICE = 'META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor'

    def "the jar holds the service file of the visitor and no other registration"() {
        given:
        def jar = new File(System.getProperty('processor.jar'))

        when:
        def registrations
        def visitors
        new ZipFile(jar).withCloseable { zip ->
            registrations = zip.entries().toList().findAll { !it.directory && it.name.startsWith('META-INF/') }*.name.findAll {
                !(it in ['META-INF/MANIFEST.MF'])
            }.sort()
            visitors = zip.getInputStream(zip.getEntry(VISITOR_SERVICE)).text.readLines().findAll { !it.isBlank() }
        }

        then:
        jar.isFile()
        registrations.findAll { it.startsWith('META-INF/services/') || it.startsWith('META-INF/gradle/') } == [VISITOR_SERVICE]
        visitors == ['io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor',
                     'io.micronaut.graphql.tools.visitor.client.GraphQLClientVisitor']
    }

    def "no class path entry of this module registers an annotation processor"() {
        expect:
        !Collections.list(getClass().classLoader.getResources('META-INF/services/javax.annotation.processing.Processor'))
                .any { it.toString().contains('graphql-tools-processor') }
        !Collections.list(getClass().classLoader.getResources('META-INF/gradle/incremental.annotation.processors'))
                .any { it.toString().contains('graphql-tools-processor') }
    }
}
