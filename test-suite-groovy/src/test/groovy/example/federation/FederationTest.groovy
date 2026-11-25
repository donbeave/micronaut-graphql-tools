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
package example.federation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Specification

// The Groovy suite runs on the JVM: a subgraph that serves _service and _entities, with strict mode at its default.
class FederationTest extends Specification {

    private static final String PREFIX = 'micronaut.graphql.tools.'

    // tag::settings[]
    private static final Map<String, Object> SETTINGS = [
            ('micronaut.graphql.tools.allow.inert-directives'): ['key'],
            ('micronaut.graphql.tools.allow.unreachable')     : ['Order']
    ] as Map<String, Object>
    // end::settings[]

    private static ExecutionResult execute(ApplicationContext context, String query, Map<String, Object> variables = [:]) {
        GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build()
                .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
    }

    void 'the service field returns the sdl of the subgraph'() {
        given:
        ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)

        when:
        def result = execute(context, '{ _service { sdl } }')
        String sdl = ((result.getData() as Map<String, Object>)._service as Map<String, Object>).sdl

        then:
        result.errors.isEmpty()
        sdl.contains('type Product @key(fields : "id")')
        sdl.contains('type Order @key(fields : "id")')

        cleanup:
        context?.close()
    }

    // tag::entities-query[]
    void 'entities resolves representations in order'() {
        given:
        ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)

        when:
        def result = execute(context, 'query ($representations: [_Any!]!) { _entities(representations: $representations) {' +
                ' __typename ... on Product { id name } ... on Order { id total } } }',
                [representations: [[__typename: 'Order', id: 'o1'], [__typename: 'Product', id: 'p1']]])

        then:
        result.errors.isEmpty()
        result.getData() == [_entities: [
                [__typename: 'Order', id: 'o1', total: 42],
                [__typename: 'Product', id: 'p1', name: 'Product p1']]]

        cleanup:
        context?.close()
    }
    // end::entities-query[]

    void 'the regular fields still work'() {
        given:
        ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)

        when:
        def result = execute(context, '{ product(id: "p2") { id name } }')

        then:
        result.errors.isEmpty()
        result.getData() == [product: [id: 'p2', name: 'Product p2']]

        cleanup:
        context?.close()
    }

    void 'invalid representations are errors'() {
        given:
        ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)
        String query = 'query ($r: [_Any!]!) { _entities(representations: $r) { __typename } }'
        List<Object> invalid = [
                ['Product'],
                [[id: 'p1']],
                [[__typename: 7, id: 'p1']],
                [[__typename: 'Unknown', id: 'p1']],
                (0..100).collect { [__typename: 'Product', id: "p${it}".toString()] }
        ]

        expect:
        invalid.every { !execute(context, query, [r: it] as Map<String, Object>).errors.isEmpty() }

        cleanup:
        context?.close()
    }

    // tag::strict[]
    void 'strict mode needs the two allow settings'() {
        expect:
        findingOf([(PREFIX + 'allow.unreachable'): ['Order']]).contains('unwired-directive')
        findingOf([(PREFIX + 'allow.inert-directives'): ['key']]).contains('unreachable-type')
    }
    // end::strict[]

    private static String findingOf(Map<String, Object> properties) {
        Throwable e = null
        try {
            ApplicationContext.run(properties, FederationFixtures.FEDERATION).close()
        } catch (Exception failure) {
            e = failure
        }
        assert e != null
        while (!(e instanceof SchemaBindingException) && e.cause != null && !e.cause.is(e)) {
            e = e.cause
        }
        assert e instanceof SchemaBindingException
        return e.message
    }

}
