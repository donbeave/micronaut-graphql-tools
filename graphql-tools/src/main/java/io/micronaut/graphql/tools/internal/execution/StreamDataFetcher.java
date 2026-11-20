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
package io.micronaut.graphql.tools.internal.execution;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;

/**
 * The fetcher of a root field of the subscription type. graphql-java resolves the selection set of every event on the
 * thread that delivers the event, and a source can deliver on an event-loop thread (a message client, a timer, a
 * WebSocket of its own) and without the request's context. The fetcher wraps the returned {@link Publisher} once for each
 * subscription: its events are moved to the executor that runs the operation (unless the operation runs on the calling
 * thread, {@code executor: none}), in order, and graphql-java receives them inside the propagated context that the
 * operation captured. Cancelling the subscription cancels the source and marks the execution cancelled.
 *
 * <p>The cost is the wrapper and one queue for each subscription, and one executor task for each burst of events, not for
 * each field. An execution that the library's invocation did not start passes through.</p>
 */
@Internal
public final class StreamDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final ConversionService conversionService;

    /**
     * @param delegate          the fetcher that invokes the resolver method
     * @param conversionService converts a reactive type that is not a {@code Publisher} to one
     */
    public StreamDataFetcher(DataFetcher<Object> delegate, ConversionService conversionService) {
        this.delegate = delegate;
        this.conversionService = conversionService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Object result = delegate.get(environment);
        Publisher<Object> publisher;
        if (result instanceof Publisher<?> direct) {
            publisher = (Publisher<Object>) direct;
        } else if (result != null && Publishers.isConvertibleToPublisher(result.getClass())) {
            publisher = Publishers.convertPublisher(conversionService, result, Publisher.class);
        } else {
            return result;
        }
        ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
        if (state == null) {
            return publisher;
        }
        Flux<Object> events = Flux.from(publisher);
        Executor executor = state.requestExecutor();
        if (executor != null) {
            events = events.publishOn(Schedulers.fromExecutor(executor));
        }
        return ReactivePropagation.propagate(state.propagatedContext(), events.doOnCancel(state::cancel));
    }
}
