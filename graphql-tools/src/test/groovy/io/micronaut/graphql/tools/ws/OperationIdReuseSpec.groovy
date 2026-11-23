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
package io.micronaut.graphql.tools.ws

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.configuration.graphql.ws.GraphQLWsConfiguration
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.micronaut.core.type.Argument
import io.micronaut.context.BeanContext
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.internal.ws.LibraryGraphQLWsHandler
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.scheduling.TaskScheduler
import io.micronaut.scheduling.executor.ExecutorSelector
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketSession
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * A client may answer the message that ends an operation at once, even before the server thread that sent it runs
 * again. The id must already be free then, so the test answers from inside the send, which is the worst case of that race.
 */
class OperationIdReuseSpec extends Specification {

    private static final String SUBSCRIBE = '{"id":"1","type":"subscribe","payload":{"query":"subscription { ticks }"}}'

    private static Object plain(JsonNode node) {
        if (node.isObject()) {
            return node.entries().collectEntries { [(it.key): plain(it.value)] }
        }
        if (node.isArray()) {
            return node.values().collect { plain(it) }
        }
        return node.value
    }

    def "the id of a finished operation is free when its completion is sent"() {
        given:
        JsonMapper json = Mock(JsonMapper)
        json.readValue(_ as String, _ as Argument) >> { String text, Argument type -> new JsonSlurper().parseText(text) }
        json.writeValueAsString(_) >> { JsonNode tree -> JsonOutput.toJson(plain(tree)) }
        GraphQLToolsConfiguration configuration = new GraphQLToolsConfiguration()
        configuration.execution.executor = 'none'
        GraphQLInvocation invocation = new GraphQLInvocation() {
            @Override
            Publisher<ExecutionResult> invoke(GraphQLInvocationData data, HttpRequest request, MutableHttpResponse<String> response) {
                Flux.just(ExecutionResultImpl.newExecutionResult()
                        .data(Flux.just(ExecutionResultImpl.newExecutionResult().data([ticks: 1]).build())).build())
            }
        }
        def handler = new LibraryGraphQLWsHandler(invocation, new GraphQLWsConfiguration(), Mock(TaskScheduler),
                json, [], configuration, Mock(ExecutorSelector), Mock(BeanContext))
        def attributes = [:]
        List<Integer> closed = []
        int completions = 0
        WebSocketSession session = Mock(WebSocketSession)
        session.isOpen() >> true
        session.put(_, _) >> { CharSequence key, Object value -> attributes[key.toString()] = value; null }
        session.get(_, _) >> { CharSequence key, Class type -> Optional.ofNullable(attributes[key.toString()]) }
        session.remove(_) >> { CharSequence key -> attributes.remove(key.toString()); null }
        session.close(_) >> { CloseReason reason -> closed << reason.code; null }
        session.send(_) >> { Object message ->
            if (message.toString().contains('"complete"') && completions++ == 0) {
                // a client that starts the next operation under the same id as soon as it reads the completion
                handler.onMessage(SUBSCRIBE, session)
            }
            Mono.empty()
        }

        when:
        handler.onOpen(session, Mock(HttpRequest))
        handler.onMessage('{"type":"connection_init"}', session)
        handler.onMessage(SUBSCRIBE, session)

        then: 'the second subscribe was accepted and ran to its own completion'
        closed == []
        completions == 2
    }

}
