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
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;

/**
 * One call: it subscribes to the single HTTP response, classifies it and completes with what its {@link Outcome} says, or with the
 * exception that {@link GraphQLResponse#dataOrThrow()} throws. Cancelling it cancels the HTTP call.
 *
 * @param <R> the type of the result
 */
@Internal
final class Call<R> extends CompletableFuture<R> implements Subscriber<HttpResponse<byte[]>> {

    private final GraphQLCodec codec;
    private final Argument<?> responseType;
    private final boolean unwrap;
    private final Outcome outcome;
    private volatile Subscription subscription;

    Call(GraphQLCodec codec, Argument<?> responseType, boolean unwrap, Outcome outcome) {
        this.codec = codec;
        this.responseType = responseType;
        this.unwrap = unwrap;
        this.outcome = outcome;
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
            finish(ResponseClassifier.classify(codec, responseType, unwrap, response, response.body()));
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    @Override
    public void onError(Throwable failure) {
        try {
            if (failure instanceof HttpClientResponseException e && e.getResponse() != null) {
                HttpResponse<?> response = e.getResponse();
                byte[] body = response.getBody(byte[].class).orElse(null);
                finish(ResponseClassifier.classify(codec, responseType, unwrap, response, body));
            } else {
                completeExceptionally(ResponseClassifier.transportFailure(failure));
            }
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void finish(GraphQLResponse<?> response) {
        Object result = switch (outcome) {
            case RESPONSE -> response;
            case DATA -> response.dataOrThrow();
            case NOTHING -> {
                response.dataOrThrow();
                yield null;
            }
        };
        complete((R) result);
    }

    @Override
    public void onComplete() {
        if (!isDone()) {
            completeExceptionally(new GraphQLTransportException("The server sent no response", GraphQLTransportException.NO_STATUS, null, false, null));
        }
    }
}
