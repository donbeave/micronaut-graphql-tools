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
package io.micronaut.graphql.tools.internal.resolver;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts the {@link Publisher} that a resolver method returns on a field outside the subscription type to the
 * {@link java.util.concurrent.CompletionStage} that graphql-java waits for. The binding decides once per field, at
 * startup, whether this fetcher wraps the method fetcher and whether the publisher emits one value or many; a field that
 * is not reactive does not get this fetcher. A call allocates one subscriber, which is also the future it returns.
 */
@Internal
public final class PublisherAdaptingDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final boolean many;

    /**
     * @param delegate the fetcher that invokes the resolver method
     * @param many     whether the publisher emits many values, which are collected into a list; otherwise it emits one
     *                 value or none
     */
    public PublisherAdaptingDataFetcher(DataFetcher<Object> delegate, boolean many) {
        this.delegate = delegate;
        this.many = many;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Object result = delegate.get(environment);
        if (!(result instanceof Publisher<?> publisher)) {
            // null, or a DataFetcherResult for an argument that did not convert
            return result;
        }
        Collector subscriber = new Collector(many);
        ((Publisher<Object>) publisher).subscribe(subscriber);
        return subscriber;
    }

    /**
     * The subscriber and the future in one object. Cancelling the future (graphql-java does on a timeout or an
     * instrumentation cancel) cancels the subscription.
     */
    private static final class Collector extends CompletableFuture<Object> implements Subscriber<Object> {

        private final boolean many;
        private volatile Subscription subscription;
        private List<Object> values;

        Collector(boolean many) {
            this.many = many;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (isDone()) {
                s.cancel();
                return;
            }
            subscription = s;
            if (many) {
                values = new ArrayList<>();
                s.request(Long.MAX_VALUE);
            } else {
                s.request(1);
            }
        }

        @Override
        public void onNext(Object value) {
            if (many) {
                values.add(value);
            } else if (complete(value)) {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable error) {
            completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            // an empty Mono is null, an empty Flux is an empty list
            complete(many ? values : null);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            Subscription s = subscription;
            if (cancelled && s != null) {
                s.cancel();
            }
            return cancelled;
        }
    }
}
