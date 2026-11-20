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
import graphql.schema.DataFetchingEnvironmentImpl
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.execution.GraphQLTransport
import io.micronaut.graphql.tools.internal.execution.ExecutorHopDataFetcher
import io.micronaut.graphql.tools.internal.execution.FieldExecutionPlanner
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * The per-field executor hop: the planner adds it only for a field that has an executor, the method runs on that
 * executor with the captured context, and a stage that the method returns is followed.
 */
class ExecutorHopSpec extends Specification {

    record Marker(String value) implements PropagatedContextElement {
    }

    ExecutorService pool = Executors.newSingleThreadExecutor { task -> new Thread(task, 'hop-pool') }

    void cleanup() {
        pool.shutdownNow()
    }

    private static DataFetchingEnvironment environment(PropagatedContext context) {
        GraphQLContext graphQlContext = GraphQLContext.newContext().build()
        new ExecutionState(context, GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), null, null).storeIn(graphQlContext)
        DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(graphQlContext).build()
    }

    void 'a field without an executor keeps the fetcher it had'() {
        given:
        DataFetcher<Object> method = { env -> 'value' } as DataFetcher<Object>
        ReturnVerdict verdict = new ReturnVerdict(0, false, null, ReturnVerdict.Reactive.NONE, false, null)

        expect:
        FieldExecutionPlanner.plan(method, verdict, false, ConversionService.SHARED, null).is(method)
        FieldExecutionPlanner.plan(method, verdict, false, ConversionService.SHARED, pool) instanceof ExecutorHopDataFetcher
    }

    void 'the method runs on the executor of the field with the context of the operation'() {
        given:
        PropagatedContext captured = PropagatedContext.empty().plus(new Marker('request'))
        DataFetcher<Object> fetcher = new ExecutorHopDataFetcher(pool, { env ->
            [Thread.currentThread().name, PropagatedContext.getOrEmpty().find(Marker).map { it.value() }.orElse(null)]
        } as DataFetcher<Object>)

        expect:
        ((CompletableFuture) fetcher.get(environment(captured))).get(5, TimeUnit.SECONDS) == ['hop-pool', 'request']
    }

    void 'a stage that the method returns is followed, and its failure is unwrapped'() {
        given:
        DataFetcher<Object> ok = new ExecutorHopDataFetcher(pool, { env -> CompletableFuture.supplyAsync { Thread.currentThread().name } } as DataFetcher<Object>)
        DataFetcher<Object> failing = new ExecutorHopDataFetcher(pool, { env ->
            CompletableFuture.failedFuture(new CompletionException(new IllegalStateException('boom')))
        } as DataFetcher<Object>)

        when:
        def value = ((CompletableFuture) ok.get(environment(PropagatedContext.empty()))).get(5, TimeUnit.SECONDS)
        ((CompletableFuture) failing.get(environment(PropagatedContext.empty()))).get(5, TimeUnit.SECONDS)

        then:
        value != null
        def e = thrown(ExecutionException)
        e.cause instanceof IllegalStateException
    }

    void 'a thrown exception and a rejected task fail the future'() {
        given:
        Executor rejecting = { Runnable r -> throw new RejectedExecutionException('full') } as Executor
        DataFetcher<Object> throwing = new ExecutorHopDataFetcher(pool, { env -> throw new IllegalArgumentException('bad') } as DataFetcher<Object>)
        DataFetcher<Object> rejected = new ExecutorHopDataFetcher(rejecting, { env -> 'never' } as DataFetcher<Object>)

        when:
        ((CompletableFuture) throwing.get(environment(PropagatedContext.empty()))).get(5, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)

        when:
        ((CompletableFuture) rejected.get(environment(PropagatedContext.empty()))).get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof RejectedExecutionException
    }
}
