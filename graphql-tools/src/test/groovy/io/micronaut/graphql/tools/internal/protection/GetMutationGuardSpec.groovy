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
package io.micronaut.graphql.tools.internal.protection

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.graphql.tools.execution.GraphQLTransport
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/** The guard refuses a mutation over GET from the document that the execution parses, with or without the cache. */
class GetMutationGuardSpec extends Specification {

    private static final String DOCUMENT = 'query Read { a } mutation Write { b }'

    private AtomicInteger parses = new AtomicInteger()

    private Function<ExecutionInput, PreparsedDocumentEntry> parse = { ExecutionInput input ->
        parses.incrementAndGet()
        new PreparsedDocumentEntry(new Parser().parseDocument(input.query))
    }

    private ExecutionInput input(String query, String operationName, HttpMethod method, GraphQLTransport transport = GraphQLTransport.HTTP) {
        def input = ExecutionInput.newExecutionInput(query).operationName(operationName).build()
        new ExecutionState(PropagatedContext.empty(), transport, HttpRequest.create(method, '/graphql'), null,
                ConvertibleValues.empty(), null, null).storeIn(input.graphQLContext)
        input
    }

    @Unroll
    void "GET of a query is not refused (cache #cached)"() {
        given:
        def guard = new GetMutationGuard(cached ? new DocumentCache(10) : null)

        when:
        def entry = guard.getDocumentAsync(input(query, name, HttpMethod.GET), parse).join()

        then:
        entry.document != null

        where:
        query    | name   | cached
        '{ a }'  | null   | false
        '{ a }'  | null   | true
        DOCUMENT | 'Read' | false
        DOCUMENT | 'Read' | true
    }

    @Unroll
    void "GET of a mutation is refused before execution (cache #cached)"() {
        given:
        def guard = new GetMutationGuard(cached ? new DocumentCache(10) : null)

        when:
        guard.getDocumentAsync(input(query, name, HttpMethod.GET), parse).join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof MutationOverGetException
        parses.get() == 1

        where:
        query                      | name    | cached
        'mutation { b }'           | null    | false
        'mutation { b }'           | null    | true
        DOCUMENT                   | 'Write' | false
        DOCUMENT                   | 'Write' | true
    }

    void "the cache keeps the parse that the guard checked"() {
        given:
        def guard = new GetMutationGuard(new DocumentCache(10))

        when:
        guard.getDocumentAsync(input('mutation { b }', null, HttpMethod.GET), parse).join()
        guard.getDocumentAsync(input('mutation { b }', null, HttpMethod.GET), parse).join()

        then:
        thrown(CompletionException)
        parses.get() == 1
    }

    @Unroll
    void "a #method request or a WebSocket operation runs a mutation"() {
        given:
        def guard = new GetMutationGuard(null)

        expect:
        guard.getDocumentAsync(input('mutation { b }', null, method, transport), parse).join().document != null

        where:
        method          | transport
        HttpMethod.POST | GraphQLTransport.HTTP
        HttpMethod.GET  | GraphQLTransport.WEBSOCKET
    }

    @Unroll
    void "a #method request is refused, and an empty operation name selects the only operation"() {
        given:
        def guard = new GetMutationGuard(null)

        when:
        guard.getDocumentAsync(input('mutation { b }', '', method), parse).join()

        then:
        thrown(CompletionException)

        where:
        method << [HttpMethod.GET, HttpMethod.HEAD]
    }
}
