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
package example.tck

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.websocket.GraphQLConnectionInterceptor
import io.micronaut.graphql.tools.websocket.GraphQLConnectionRequest
import io.micronaut.graphql.tools.websocket.GraphQLConnectionResult
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import reactor.core.publisher.Flux
import reactor.core.scheduler.NonBlocking

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `websocket`: a connection interceptor that accepts a token, a subscription whose events
// come from a thread that declares itself an event-loop thread, and a counter of the streams that were cancelled.

// tag::ws-interceptor[]
/** Accepts a connection whose `connection_init` payload carries the token, and rejects every other. */
@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'websocket')
class TokenInterceptor implements GraphQLConnectionInterceptor {

    @Override
    GraphQLConnectionResult intercept(GraphQLConnectionRequest request) {
        if (request.payload().get('token') == 'secret') {
            return GraphQLConnectionResult.accept([user: 'alice'] as Map<String, Object>)
        }
        return GraphQLConnectionResult.reject()
    }
}
// end::ws-interceptor[]

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'websocket')
class CancelledStreams {
    final AtomicInteger count = new AtomicInteger()
    final AtomicInteger echoKeys = new AtomicInteger()
}

/** Counts the keys that reach its batch function. */
@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'websocket')
class EchoLoader implements BatchLoader<Integer, String> {

    private final CancelledStreams counters

    EchoLoader(CancelledStreams counters) {
        this.counters = counters
    }

    @Override
    CompletionStage<List<String>> load(List<Integer> keys) {
        counters.echoKeys.addAndGet(keys.size())
        return CompletableFuture.completedFuture(keys.collect { Integer key -> 'e' + key } as List<String>)
    }
}

@CompileStatic
@GraphQLType
class Tick {
    final int number
    final boolean sourceNonBlocking

    Tick(int number, boolean sourceNonBlocking) {
        this.number = number
        this.sourceNonBlocking = sourceNonBlocking
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'websocket')
class WebSocketQueries {

    private final CancelledStreams cancelled

    WebSocketQueries(CancelledStreams cancelled) {
        this.cancelled = cancelled
    }

    boolean onEventLoop() {
        TimeUnit.MILLISECONDS.sleep(50)
        return Thread.currentThread() instanceof NonBlocking
    }

    String connectionUser(DataFetchingEnvironment environment) {
        return ExecutionState.of(environment.graphQlContext).connectionAttributes().get('user', String).orElse(null)
    }

    int cancelledStreams() {
        return cancelled.count.get()
    }

    int echoLoaderKeys() {
        return cancelled.echoKeys.get()
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'websocket')
class WebSocketSubscriptions {

    private final CancelledStreams cancelled

    WebSocketSubscriptions(CancelledStreams cancelled) {
        this.cancelled = cancelled
    }

    // tag::ws-subscription[]
    Flux<Tick> ticks(int count) {
        // the timer of Reactor emits on a thread that declares itself an event-loop thread
        return Flux.interval(Duration.ofMillis(10))
                .take(count)
                .map { Long number -> new Tick(number.intValue(), Thread.currentThread() instanceof NonBlocking) }
    }
    // end::ws-subscription[]

    Flux<Tick> endless() {
        return Flux.interval(Duration.ofMillis(20))
                .map { Long number -> new Tick(number.intValue(), true) }
                .doOnCancel { cancelled.count.incrementAndGet() }
    }
}

@CompileStatic
@Singleton
@GraphQLTypeResolver(Tick)
@Requires(property = 'tck.scenario', value = 'websocket')
class TickFields {

    boolean onEventLoop(Tick tick) {
        return Thread.currentThread() instanceof NonBlocking
    }

    CompletionStage<String> echo(Tick tick, DataLoader<Integer, String> loader) {
        return loader.load(1)
    }

    CompletionStage<String> echoAgain(Tick tick, DataLoader<Integer, String> loader) {
        return loader.load(1)
    }

    String requestHeader(Tick tick) {
        return ServerRequestContext.currentRequest().map { it.headers.get('X-Tck') }.orElse(null)
    }
}
