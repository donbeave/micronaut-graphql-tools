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
package example.tck;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer;
import io.micronaut.graphql.tools.error.GraphQLExceptionContext;
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionInterceptor;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionRequest;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionResult;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.NonBlocking;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenario {@code websocket}: a connection interceptor that accepts a token, a subscription whose
 * events come from a thread that declares itself an event-loop thread, and a counter of the streams that were cancelled.
 */
public final class WebSocketScenario {

    static final String SCENARIO = "websocket";

    private WebSocketScenario() {
    }

    // tag::ws-interceptor[]
    /** Accepts a connection whose {@code connection_init} payload carries the token, and rejects every other. */
    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TokenInterceptor implements GraphQLConnectionInterceptor {

        @Override
        public GraphQLConnectionResult intercept(GraphQLConnectionRequest request) {
            if ("secret".equals(request.payload().get("token"))) {
                return GraphQLConnectionResult.accept(Map.of("user", "alice"));
            }
            return GraphQLConnectionResult.reject();
        }
    }
    // end::ws-interceptor[]

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class CancelledStreams {

        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger echoKeys = new AtomicInteger();
        final List<String> operations = new CopyOnWriteArrayList<>();
    }

    /** Counts the keys that reach its batch function. */
    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class EchoLoader implements BatchLoader<Integer, String> {

        private final CancelledStreams counters;

        public EchoLoader(CancelledStreams counters) {
            this.counters = counters;
        }

        @Override
        public CompletionStage<List<String>> load(List<Integer> keys) {
            counters.echoKeys.addAndGet(keys.size());
            return CompletableFuture.completedFuture(keys.stream().map(key -> "e" + key).toList());
        }
    }

    public static class TickFailure extends RuntimeException {

        public TickFailure() {
            super("secret internal detail");
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TickFailureMapper implements GraphQLExceptionMapper<TickFailure> {

        @Override
        public GraphQLError map(TickFailure exception, GraphQLExceptionContext context) {
            return context.error("tick failed", Map.of("code", "TICK_FAILED"));
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class StreamCustomizer implements GraphQLErrorsCustomizer {

        @Override
        public List<GraphQLError> customize(List<GraphQLError> errors) {
            return errors.stream().map(error -> {
                Map<String, Object> extensions = new LinkedHashMap<>(error.getExtensions() == null ? Map.of() : error.getExtensions());
                extensions.put("stream", "customized");
                return (GraphQLError) GraphqlErrorBuilder.newError()
                        .message(error.getMessage()).path(error.getPath()).locations(error.getLocations())
                        .errorType(error.getErrorType()).extensions(extensions).build();
            }).toList();
        }
    }

    @GraphQLType
    public static class Tick {

        final int number;
        final boolean sourceNonBlocking;

        Tick(int number, boolean sourceNonBlocking) {
            this.number = number;
            this.sourceNonBlocking = sourceNonBlocking;
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        private final CancelledStreams cancelled;

        public Queries(CancelledStreams cancelled) {
            this.cancelled = cancelled;
        }

        public boolean onEventLoop() throws InterruptedException {
            TimeUnit.MILLISECONDS.sleep(50);
            return Thread.currentThread() instanceof NonBlocking;
        }

        public String connectionUser(DataFetchingEnvironment environment) {
            return ExecutionState.of(environment.getGraphQlContext()).getConnectionAttributes().get("user", String.class).orElse(null);
        }

        public String requestHeader() {
            return ServerRequestContext.currentRequest().map(request -> request.getHeaders().get("X-Tck")).orElse(null);
        }

        public String nothing() {
            return null;
        }

        public String transport(DataFetchingEnvironment environment) {
            return ExecutionState.of(environment.getGraphQlContext()).getTransport().name();
        }

        public int cancelledStreams() {
            return cancelled.count.get();
        }

        public List<String> cancelledOperations() {
            return List.copyOf(cancelled.operations);
        }

        public int echoLoaderKeys() {
            return cancelled.echoKeys.get();
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Subscriptions {

        private final CancelledStreams cancelled;

        public Subscriptions(CancelledStreams cancelled) {
            this.cancelled = cancelled;
        }

        public Flux<Tick> failing() {
            return Flux.error(new IllegalStateException("secret internal detail"));
        }

        public Flux<Tick> failingMapped() {
            return Flux.error(new TickFailure());
        }

        public Flux<Tick> brokenEvent() {
            return Flux.just(new Tick(1, true));
        }

        // tag::ws-subscription[]
        public Flux<Tick> ticks(int count) {
            // the timer of Reactor emits on a thread that declares itself an event-loop thread
            return Flux.interval(Duration.ofMillis(10))
                    .take(count)
                    .map(number -> new Tick(number.intValue(), Thread.currentThread() instanceof NonBlocking));
        }
        // end::ws-subscription[]

        public Flux<Tick> tracked(int count, String operation) {
            return ticks(count).doOnCancel(() -> cancelled.operations.add(operation));
        }

        public Flux<Tick> endless() {
            return Flux.interval(Duration.ofMillis(20))
                    .map(number -> new Tick(number.intValue(), true))
                    .doOnCancel(cancelled.count::incrementAndGet);
        }
    }

    @Singleton
    @GraphQLTypeResolver(Tick.class)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TickFields {

        public int number(Tick tick) {
            return tick.number;
        }

        public boolean sourceNonBlocking(Tick tick) {
            return tick.sourceNonBlocking;
        }

        public boolean onEventLoop(Tick tick) {
            return Thread.currentThread() instanceof NonBlocking;
        }

        public CompletionStage<String> echo(Tick tick, DataLoader<Integer, String> loader) {
            return loader.load(1);
        }

        public CompletionStage<String> echoAgain(Tick tick, DataLoader<Integer, String> loader) {
            return loader.load(1);
        }

        public String broken(Tick tick) {
            throw new IllegalStateException("secret internal detail");
        }

        public String requestHeader(Tick tick) {
            return ServerRequestContext.currentRequest().map(request -> request.getHeaders().get("X-Tck")).orElse(null);
        }
    }
}
