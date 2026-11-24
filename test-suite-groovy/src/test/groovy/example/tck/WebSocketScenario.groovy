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

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer
import io.micronaut.graphql.tools.error.GraphQLExceptionContext
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper
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
import java.util.concurrent.CopyOnWriteArrayList
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
    final List<String> operations = new CopyOnWriteArrayList<String>()
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

class TickFailure extends RuntimeException {
    TickFailure() {
        super('secret internal detail')
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'websocket')
class TickFailureMapper implements GraphQLExceptionMapper<TickFailure> {

    @Override
    GraphQLError map(TickFailure exception, GraphQLExceptionContext context) {
        return context.error('tick failed', [code: 'TICK_FAILED'] as Map<String, Object>)
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'websocket')
class StreamCustomizer implements GraphQLErrorsCustomizer {

    @Override
    List<GraphQLError> customize(List<GraphQLError> errors) {
        return errors.collect { GraphQLError error ->
            Map<String, Object> extensions = new LinkedHashMap<String, Object>(error.extensions ?: [:] as Map<String, Object>)
            extensions.put('stream', 'customized')
            (GraphQLError) GraphqlErrorBuilder.newError()
                    .message(error.message).path(error.path).locations(error.locations)
                    .errorType(error.errorType).extensions(extensions).build()
        }
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
        return ExecutionState.of(environment.graphQlContext).connectionAttributes.get('user', String).orElse(null)
    }

    String requestHeader() {
        return ServerRequestContext.currentRequest().map { it.headers.get('X-Tck') }.orElse(null)
    }

    String nothing() {
        return null
    }

    String transport(DataFetchingEnvironment environment) {
        return ExecutionState.of(environment.graphQlContext).transport.name()
    }

    int cancelledStreams() {
        return cancelled.count.get()
    }

    List<String> cancelledOperations() {
        return new ArrayList<String>(cancelled.operations)
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

    Flux<Tick> failing() {
        return Flux.error(new IllegalStateException('secret internal detail'))
    }

    Flux<Tick> failingMapped() {
        return Flux.error(new TickFailure())
    }

    Flux<Tick> brokenEvent() {
        return Flux.just(new Tick(1, true))
    }

    // tag::ws-subscription[]
    Flux<Tick> ticks(int count) {
        // the timer of Reactor emits on a thread that declares itself an event-loop thread
        return Flux.interval(Duration.ofMillis(10))
                .take(count)
                .map { Long number -> new Tick(number.intValue(), Thread.currentThread() instanceof NonBlocking) }
    }
    // end::ws-subscription[]

    Flux<Tick> tracked(int count, String operation) {
        return ticks(count).doOnCancel { cancelled.operations.add(operation) }
    }

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

    String broken(Tick tick) {
        throw new IllegalStateException('secret internal detail')
    }

    String requestHeader(Tick tick) {
        return ServerRequestContext.currentRequest().map { it.headers.get('X-Tck') }.orElse(null)
    }
}
