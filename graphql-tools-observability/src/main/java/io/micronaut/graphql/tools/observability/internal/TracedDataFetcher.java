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
import io.micronaut.graphql.tools.observability.GraphQLObservability;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.concurrent.CompletionStage;

/**
 * Runs the fetcher of one selected field inside a span: a child of the request span, current while the fetcher runs, and ended when
 * the value is there (when the fetcher returns it, or when the future it returns completes).
 */
@Internal
final class TracedDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<?> delegate;
    private final Tracer tracer;
    private final String parentType;
    private final String field;
    private final String spanName;

    TracedDataFetcher(DataFetcher<?> delegate, Tracer tracer, String parentType, String field) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.parentType = parentType;
        this.field = field;
        this.spanName = parentType + "." + field;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Span request = environment.getGraphQlContext().get(TracingObserver.SPAN_KEY);
        Context parent = request == null ? Context.current() : Context.current().with(request);
        Span span = tracer.spanBuilder(spanName).setParent(parent)
                .setAttribute(GraphQLObservability.FIELD_PARENT_ATTRIBUTE, parentType)
                .setAttribute(GraphQLObservability.FIELD_NAME_ATTRIBUTE, field)
                .startSpan();
        Object value;
        try (Scope ignored = span.makeCurrent()) {
            value = delegate.get(environment);
        } catch (Exception | Error e) {
            fail(span, e);
            span.end();
            throw e;
        }
        if (value instanceof CompletionStage<?> stage) {
            return stage.whenComplete((result, failure) -> {
                if (failure != null) {
                    fail(span, failure);
                }
                span.end();
            });
        }
        span.end();
        return value;
    }

    private static void fail(Span span, Throwable failure) {
        span.recordException(failure);
        span.setStatus(StatusCode.ERROR);
    }

}
