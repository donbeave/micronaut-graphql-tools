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

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletionStage;
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
 *
 * <p>An error that the publisher signals ends the stream with a {@link SubscriptionFailure}, which carries the errors that
 * the default error handler (the mappers and the generic error) made of the exception, so that a transport sends the
 * same errors as for a query. A transport that does not know the class sees the original exception.</p>
 */
@Internal
public final class StreamDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final ConversionService conversionService;
    @Nullable
    private final DataFetcherExceptionHandler errorHandler;

    /**
     * @param delegate          the fetcher that invokes the resolver method
     * @param conversionService converts a reactive type that is not a {@code Publisher} to one
     * @param errorHandler      makes the errors of an exception that the publisher signals, or null to pass it on as it is
     */
    public StreamDataFetcher(DataFetcher<Object> delegate, ConversionService conversionService,
                             @Nullable DataFetcherExceptionHandler errorHandler) {
        this.delegate = delegate;
        this.conversionService = conversionService;
        this.errorHandler = errorHandler;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
        if (state != null) {
            state.markSubscription();
        }
        Object result = delegate.get(environment);
        if (result instanceof CompletionStage<?> stage) {
            // CompletionStage<Publisher<T>>: graphql-java unwraps the stage later, so the publisher is wrapped when it arrives
            return stage.thenApply(value -> wrap(value, state, environment));
        }
        return wrap(result, state, environment);
    }

    @SuppressWarnings("unchecked")
    private Object wrap(Object result, ExecutionState state, DataFetchingEnvironment environment) {
        Publisher<Object> publisher;
        if (result instanceof Publisher<?> direct) {
            publisher = (Publisher<Object>) direct;
        } else if (result != null && Publishers.isConvertibleToPublisher(result.getClass())) {
            publisher = Publishers.convertPublisher(conversionService, result, Publisher.class);
        } else {
            return result;
        }
        if (state == null) {
            // not run by the library transport, which alone understands SubscriptionFailure: the publisher passes on as it is
            return publisher;
        }
        Flux<Object> events = Flux.from(publisher);
        Executor executor = state.requestExecutor();
        if (executor != null) {
            events = events.publishOn(Schedulers.fromExecutor(executor));
        }
        if (errorHandler != null) {
            // after the hop, so that the exception mappers do not run on the thread that the source signals from
            events = events.onErrorMap(failure -> failure instanceof SubscriptionFailure ? failure : failed(failure, environment));
        }
        return ReactivePropagation.propagate(state.propagatedContext(), events.doOnCancel(state::cancel));
    }

    private Throwable failed(Throwable failure, DataFetchingEnvironment environment) {
        DataFetcherExceptionHandlerParameters parameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(failure)
                .build();
        return new SubscriptionFailure(failure, errorHandler.handleException(parameters).join().getErrors());
    }
}
