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

import graphql.GraphQLContext
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.internal.execution.StreamDataFetcher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class StreamDataFetcherSpec extends Specification {

    void "a publisher that arrives through a CompletionStage is wrapped too, so cancelling it marks the execution cancelled"() {
        given:
        def state = new ExecutionState(PropagatedContext.empty(), GraphQLTransport.WEBSOCKET, null, null, ConvertibleValues.empty(), null, null)
        def context = GraphQLContext.newContext().build()
        state.storeIn(context)
        def environment = Stub(DataFetchingEnvironment) { getGraphQlContext() >> context }
        DataFetcher<Object> method = { env -> CompletableFuture.completedFuture(Flux.never()) }

        when:
        def stage = new StreamDataFetcher(method, ConversionService.SHARED, null).get(environment) as CompletionStage
        def events = Flux.from(stage.toCompletableFuture().get() as org.reactivestreams.Publisher)
        def subscription = events.subscribe()
        subscription.dispose()

        then:
        state.cancelled
    }
}
