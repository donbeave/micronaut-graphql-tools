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
package io.micronaut.graphql.tools.execution

import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.GraphqlErrorException
import graphql.schema.DataFetchingEnvironment
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono

/** The per-execution context contributors: order, thread, context, the request view and the failure rules. */
class ContextContributorSpec extends AbstractTest {

    static final String SPEC = 'ContextContributorSpec'

    static final List<String> CALLS = Collections.synchronizedList([])
    static final List<Thread> THREADS = Collections.synchronizedList([])
    static final List<Object> SEEN = Collections.synchronizedList([])
    static final List<String> RESOLVED = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  value(key: String!): String
}
"""

    void setup() {
        CALLS.clear()
        THREADS.clear()
        SEEN.clear()
        RESOLVED.clear()
    }

    private ExecutionResult run(String query = '{ a: value(key: "first") b: value(key: "second") }', HttpRequest request = null) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), request, null)).block()
    }

    void "a contributor puts values in the context that the resolvers read"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run()

        then:
        result.errors.isEmpty()
        result.data == [a: 'one', b: 'two']
    }

    void "contributors run once per execution, in order, on the execution executor and inside the propagated context"() {
        given:
        startContext(SCHEMA, SPEC)
        def marker = new Marker('request-1')

        when:
        def scope = PropagatedContext.getOrEmpty().plus(marker).propagate()
        try {
            run()
            run()
        } finally {
            scope.close()
        }

        then: 'twice, not once per field, in the order of @Order'
        CALLS == ['first', 'second', 'first', 'second']

        and: 'off the calling thread, with the context of the caller'
        THREADS.every { it != Thread.currentThread() }
        SEEN.findAll { it instanceof Marker }.size() == 4
    }

    void "the request view says what the transport is, and has no request or response for a direct call"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        run()

        then:
        def request = SEEN.find { it instanceof GraphQLExecutionRequest } as GraphQLExecutionRequest
        request.transport() == GraphQLTransport.HTTP
        request.httpRequest() == null
        !request.httpResponse().isPresent()
        request.connectionAttributes().isEmpty()
        request.executionInput().query.contains('value')
    }

    void "a GraphQLError thrown by a contributor is the only error and no resolver runs"() {
        given:
        startContext(SCHEMA, SPEC + '.reject')

        when:
        def result = run()

        then:
        result.data == null
        result.errors.size() == 1
        result.errors[0].message == 'not allowed'
        result.errors[0].extensions.code == 'FORBIDDEN'
        RESOLVED.isEmpty()
    }

    void "any other exception of a contributor becomes one generic error and is logged, without its message"() {
        given:
        startContext(SCHEMA, SPEC + '.explode')

        when:
        def result = run()

        then:
        result.data == null
        result.errors.size() == 1
        !result.errors[0].message.contains('secret detail')
        result.errors[0].extensions.code == 'INTERNAL_ERROR'
        RESOLVED.isEmpty()
    }

    void "without contributor beans nothing is added and the operation runs"() {
        given:
        startContext(SCHEMA, SPEC + '.none')

        when:
        def result = run()

        then:
        result.errors.isEmpty()
        result.data == [a: 'none', b: 'none']
    }

    record Marker(String id) implements PropagatedContextElement {
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @Order(2)
    static class SecondContributor implements GraphQLContextContributor {
        @Override
        void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            CALLS << 'second'
            THREADS << Thread.currentThread()
            SEEN << request
            SEEN.addAll(PropagatedContext.getOrEmpty().findAll(Marker).toList())
            context.put('second', 'two')
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @Order(-5)
    static class FirstContributor implements GraphQLContextContributor {
        @Override
        void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            CALLS << 'first'
            THREADS << Thread.currentThread()
            SEEN.addAll(PropagatedContext.getOrEmpty().findAll(Marker).toList())
            context.put('first', 'one')
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.reject')
    @Singleton
    static class RejectingContributor implements GraphQLContextContributor {
        @Override
        void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            throw GraphqlErrorException.newErrorException().message('not allowed').extensions([code: 'FORBIDDEN']).build()
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.explode')
    @Singleton
    static class ExplodingContributor implements GraphQLContextContributor {
        @Override
        void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            throw new IllegalStateException('secret detail')
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.(reject|explode|none))?')
    @GraphQLRootResolver
    static class Query {
        String value(String key, DataFetchingEnvironment environment) {
            RESOLVED << key
            environment.graphQlContext.getOrDefault(key, 'none')
        }
    }

}
