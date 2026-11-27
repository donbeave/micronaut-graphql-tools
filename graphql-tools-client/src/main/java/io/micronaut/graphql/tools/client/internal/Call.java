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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One call: it subscribes to the single HTTP response, classifies it and completes with the GraphQL response or the exception.
 * Cancelling it cancels the HTTP call.
 *
 * @param <D> the type of the {@code data}
 */
@Internal
final class Call<D> extends CompletableFuture<GraphQLResponse<D>> implements Subscriber<HttpResponse<byte[]>> {

    private final GraphQLCodec codec;
    private final GraphQLRequest<D> request;
    private final boolean unwrap;
    private final Consumer<HttpHeaders> observer;
    private volatile Subscription subscription;

    Call(GraphQLCodec codec, GraphQLRequest<D> request, boolean unwrap, @Nullable Consumer<HttpHeaders> observer) {
        this.observer = observer;
        this.codec = codec;
        this.request = request;
        this.unwrap = unwrap;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        Subscription current = subscription;
        if (cancelled && current != null) {
            current.cancel();
        }
        return cancelled;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        if (isDone()) {
            s.cancel();
        } else {
            s.request(1);
        }
    }

    @Override
    public void onNext(HttpResponse<byte[]> response) {
        try {
            observe(response);
            complete(ResponseClassifier.classify(codec, request, unwrap, response, response.body()));
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    @Override
    public void onError(Throwable failure) {
        try {
            if (failure instanceof HttpClientResponseException e && e.getResponse() != null) {
                HttpResponse<?> response = e.getResponse();
                observe(response);
                byte[] body = response.getBody(byte[].class).orElse(null);
                complete(ResponseClassifier.classify(codec, request, unwrap, response, body));
            } else {
                completeExceptionally(ResponseClassifier.transportFailure(failure));
            }
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    private void observe(HttpResponse<?> response) {
        if (observer != null) {
            observer.accept(response.getHeaders());
        }
    }

    @Override
    public void onComplete() {
        if (!isDone()) {
            completeExceptionally(new GraphQLTransportException("The server sent no response", GraphQLTransportException.NO_STATUS, null, false, null));
        }
    }
}
