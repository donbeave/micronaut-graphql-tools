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
package io.micronaut.graphql.tools.client

import io.micronaut.core.type.Argument
import spock.lang.Specification

class GraphQLRequestSpec extends Specification {

    void "the builder defaults to an anonymous query with a map as data and no variables or extensions"() {
        when:
        def request = GraphQLRequest.builder().document('{ a }').build()

        then:
        request.document == '{ a }'
        request.operationName == null
        request.operationType == OperationType.QUERY
        request.variables.isEmpty()
        request.extensions.isEmpty()
        request.dataType == Argument.mapOf(String, Object)
    }

    void "the builder takes the data type as a class or an argument"() {
        expect:
        GraphQLRequest.builder(String).document('{ a }').build().dataType == Argument.of(String)
        GraphQLRequest.builder(Argument.listOf(String)).document('{ a }').build().dataType == Argument.listOf(String)
    }

    void "the builder keeps every field and the request is immutable"() {
        given:
        def variables = [a: 1]
        def request = GraphQLRequest.builder().document('mutation M { m }').operationName('M').operationType(OperationType.MUTATION)
                .variables(variables).variable('b', null).extension('e', true).extensions([f: 2]).build()
        variables.put('late', 3)

        expect:
        request.operationName == 'M'
        request.operationType == OperationType.MUTATION
        request.variables == [a: 1, b: null]
        request.extensions == [e: true, f: 2]

        when:
        request.variables.put('x', 1)

        then:
        thrown(UnsupportedOperationException)
    }

    void "a document is required"() {
        when:
        GraphQLRequest.builder().document(document).build()

        then:
        thrown(IllegalStateException)

        where:
        document << [null, '', '  ']
    }

    void "a null operation type is rejected"() {
        when:
        GraphQLRequest.builder().operationType(null)

        then:
        thrown(NullPointerException)
    }
}
