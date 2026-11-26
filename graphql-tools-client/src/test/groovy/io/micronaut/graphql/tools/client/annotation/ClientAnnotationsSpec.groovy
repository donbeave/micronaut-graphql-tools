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
package io.micronaut.graphql.tools.client.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Target

class ClientAnnotationsSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    private AnnotationMetadata metadata(Class<?> type) {
        context.getBeanDefinition(type).annotationMetadata
    }

    private AnnotationMetadata method(Class<?> type, String name) {
        context.getBeanDefinition(type).executableMethods.find { it.methodName == name }.annotationMetadata
    }

    def "value is the service id of the Client stereotype"() {
        given:
        def metadata = metadata(ClientFixtures.ValueClient)

        expect:
        metadata.hasStereotype(Client)
        metadata.stringValue(Client, 'id').get() == 'books'
        metadata.stringValue(Client).get() == 'books'
        metadata.stringValue(GraphQLClient).get() == 'books'
        metadata.stringValue(GraphQLClient, 'id').get() == 'books'
        metadata.stringValue(Client, 'path').orElse('') == ''
    }

    def "id, endpoint and configuration resolve to the Client members"() {
        given:
        def metadata = metadata(ClientFixtures.FullClient)

        expect:
        metadata.stringValue(Client, 'id').get() == 'authors'
        metadata.stringValue(Client).get() == 'authors'
        metadata.stringValue(GraphQLClient).get() == 'authors'
        metadata.stringValue(Client, 'path').get() == '/api/graphql'
        metadata.classValue(Client, 'configuration').get() == ClientFixtures.CustomConfiguration
        metadata.stringValue(GraphQLClient, 'webSocketEndpoint').get() == '/api/ws'
        metadata.stringValue(GraphQLClient, 'schema').get() == 'library'
    }

    def "a client without a value keeps the defaults"() {
        given:
        def metadata = metadata(ClientFixtures.BareClient)

        expect:
        metadata.hasStereotype(Client)
        metadata.stringValue(Client, 'id').orElse('') == ''
        metadata.stringValue(GraphQLClient, 'webSocketEndpoint').orElse('') == ''
        metadata.stringValue(GraphQLClient, 'schema').orElse('') == ''
        !metadata.classValue(Client, 'configuration').filter { it != HttpClientConfiguration }.isPresent()
    }

    def "an operation carries its document, name and method"() {
        given:
        def author = method(ClientFixtures.FullClient, 'author')
        def plain = method(ClientFixtures.FullClient, 'plain')

        expect:
        author.stringValue(GraphQLOperation).get().startsWith('query($id')
        author.stringValue(GraphQLOperation, 'name').get() == 'author'
        author.stringValue(GraphQLOperation, 'method').get() == 'GET'
        plain.hasAnnotation(GraphQLOperation)
        plain.stringValue(GraphQLOperation).orElse('') == ''
        plain.stringValue(GraphQLOperation, 'method').orElse('POST') == 'POST'
    }

    def "a variable names its parameter"() {
        given:
        def method = ClientFixtures.FullClient.getMethod('author', String)

        expect:
        method.parameters[0].getAnnotation(GraphQLVariable).value() == 'id'
        ClientFixtures.FullClient.getMethod('plain', String).parameters[0].getAnnotation(GraphQLVariable) == null
    }

    def "the metadata annotation is read from the operation method"() {
        given:
        def touch = method(ClientFixtures.FullClient, 'touch')

        expect:
        touch.stringValue(GraphQLOperationMetadata, 'document').get() == 'mutation Touch { touch }'
        touch.stringValue(GraphQLOperationMetadata, 'operationName').get() == 'Touch'
        touch.stringValue(GraphQLOperationMetadata, 'operationType').get() == 'mutation'
        !method(ClientFixtures.FullClient, 'plain').hasAnnotation(GraphQLOperationMetadata)
    }

    def "the client annotation marks a type, a field and a parameter"() {
        expect:
        GraphQLClient.getAnnotation(Target).value().toList().toSet() ==
                [ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER].toSet()
        ClientFixtures.InjectionPoints.getField('field').getAnnotation(GraphQLClient).value() == 'inline'
        ClientFixtures.InjectionPoints.getMethod('accept', Object).parameters[0].getAnnotation(GraphQLClient).value() == 'param'
    }
}
