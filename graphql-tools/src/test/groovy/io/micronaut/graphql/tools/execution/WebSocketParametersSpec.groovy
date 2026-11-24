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
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.context.ServerHttpRequestContext
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.internal.execution.LibraryGraphQLInvocation
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.configuration.graphql.GraphQLInvocationData
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.security.Principal

/**
 * What the request, response and principal parameters get on a WebSocket operation: the upgrade request of the connection
 * and its principal, no response (a plain response parameter fails with HTTP_RESPONSE_UNAVAILABLE, see ResolverParametersSpec).
 */
class WebSocketParametersSpec extends AbstractTest {

    static final String SPEC = 'WebSocketParametersSpec'

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Query {
  request: String
  response: String
  optionalResponse: Boolean
  principal: String
  currentPath: String
}

type Mutation {
  mutationRequest: String
  mutationResponse: String
  mutationOptionalResponse: Boolean
  mutationPrincipal: String
}

type Subscription {
  subscriptionRequest: String
  subscriptionResponse: String
  subscriptionOptionalResponse: Boolean
  subscriptionPrincipal: String
}
"""

    private static String named(String prefix, String field) {
        prefix.isEmpty() ? field : prefix + field.capitalize()
    }

    private Object over(GraphQLTransport transport, String operation, String field) {
        def request = HttpRequest.GET('/graphql-ws')
        request.setUserPrincipal({ 'alice' } as Principal)
        ExecutionResult result = Mono.from(applicationContext.getBean(LibraryGraphQLInvocation)
                .execute(new GraphQLInvocationData(operation + ' { ' + field + ' }', null, [:]), transport, request, null,
                        ConvertibleValues.empty())).block()
        assert result.errors.isEmpty()
        Object data = result.data
        if (data instanceof Publisher) {
            data = Flux.from((Publisher<ExecutionResult>) data).blockFirst().data
        }
        data[field]
    }

    void "a #operation over WebSocket: the request is the upgrade request, the principal is its principal and the optional response is empty"() {
        given:
        startContext(SCHEMA, SPEC)

        expect:
        over(GraphQLTransport.WEBSOCKET, operation, named(prefix, 'request')) == '/graphql-ws'
        over(GraphQLTransport.WEBSOCKET, operation, named(prefix, 'principal')) == 'alice'
        over(GraphQLTransport.WEBSOCKET, operation, named(prefix, 'optionalResponse')) == false

        where:
        operation      | prefix
        'query'        | ''
        'mutation'     | 'mutation'
        'subscription' | 'subscription'
    }

    void "a thread that holds the context of another request does not change the request of a WebSocket operation"() {
        given:
        startContext(SCHEMA, SPEC)
        def foreign = PropagatedContext.empty().plus(new ServerHttpRequestContext(HttpRequest.GET('/foreign')))

        when:
        def first = foreign.propagate().withCloseable { over(GraphQLTransport.WEBSOCKET, 'query', 'currentPath') }
        def second = over(GraphQLTransport.WEBSOCKET, 'query', 'currentPath')

        then:
        first == '/graphql-ws'
        second == '/graphql-ws'
    }

    void "over HTTP the same parameters receive the real request, principal and response"() {
        given:
        startContext(SCHEMA, SPEC)
        def request = HttpRequest.GET('/graphql')
        request.setUserPrincipal({ 'alice' } as Principal)
        def response = io.micronaut.http.HttpResponse.ok()

        when:
        ExecutionResult result = Mono.from(applicationContext.getBean(LibraryGraphQLInvocation)
                .invoke(new GraphQLInvocationData('{ request principal response optionalResponse }', null, [:]), request, response)).block()

        then:
        result.data == [request: '/graphql', principal: 'alice', response: 'true', optionalResponse: true]
    }

    void "over WebSocket without an upgrade request the request and the principal are null"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = Mono.from(applicationContext.getBean(LibraryGraphQLInvocation)
                .execute(new GraphQLInvocationData('{ request principal }', null, [:]), GraphQLTransport.WEBSOCKET, null, null,
                        ConvertibleValues.empty())).block()

        then:
        result.data == [request: null, principal: null]
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @GraphQLRootResolver
    static class Resolver {

        String request(HttpRequest<?> request) { request?.path }

        String response(MutableHttpResponse<?> response) { String.valueOf(response != null ? 'true' : 'null') }

        boolean optionalResponse(Optional<MutableHttpResponse<?>> response) { response.isPresent() }

        String principal(Principal principal) { principal?.name }

        String currentPath() { ServerRequestContext.currentRequest().map { it.path }.orElse(null) }

        String mutationRequest(HttpRequest<?> request) { request?.path }

        String mutationResponse(MutableHttpResponse<?> response) { String.valueOf(response != null ? 'true' : 'null') }

        boolean mutationOptionalResponse(Optional<MutableHttpResponse<?>> response) { response.isPresent() }

        String mutationPrincipal(Principal principal) { principal?.name }

        Publisher<String> subscriptionRequest(HttpRequest<?> request) { Flux.just(request?.path) }

        Publisher<String> subscriptionResponse(MutableHttpResponse<?> response) { Flux.just(response != null ? 'true' : 'null') }

        Publisher<Boolean> subscriptionOptionalResponse(Optional<MutableHttpResponse<?>> response) { Flux.just(response.isPresent()) }

        Publisher<String> subscriptionPrincipal(Principal principal) { Flux.just(principal?.name) }
    }
}
