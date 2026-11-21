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
package io.micronaut.graphql.tools.observability.internal;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Runs the fetcher of one selected field inside a span: a child of the request span, current while the fetcher runs, and ended
 * when the value is there (when the fetcher returns it, or when the future it returns completes).
 */
@Internal
final class FieldSpanFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final Instrumenter<DataFetchingEnvironment, Void> instrumenter;

    FieldSpanFetcher(DataFetcher<Object> delegate, Instrumenter<DataFetchingEnvironment, Void> instrumenter) {
        this.delegate = delegate;
        this.instrumenter = instrumenter;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Context requestContext = environment.getGraphQlContext().get(RequestSpanObserver.CONTEXT_KEY);
        Context parent = requestContext != null ? requestContext : Context.current();
        if (!instrumenter.shouldStart(parent, environment)) {
            return delegate.get(environment);
        }
        Context context = instrumenter.start(parent, environment);
        Object value;
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new SpanPropagatedContext(context)).propagate()) {
            value = delegate.get(environment);
        } catch (Exception | Error e) {
            instrumenter.end(context, environment, null, e);
            throw e;
        }
        if (value instanceof CompletionStage<?> stage) {
            return stage.whenComplete((result, failure) -> instrumenter.end(context, environment, null, unwrap(failure)));
        }
        instrumenter.end(context, environment, null, null);
        return value;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while (result instanceof CompletionException && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

}
