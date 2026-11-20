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

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.observability.GraphQLObservability;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Singleton;

/**
 * Starts one span for each request, a child of the span that is current when the execution begins, and puts it in the GraphQL
 * context so that the spans of the selected fields are its children.
 */
@Internal
@Singleton
@Requires(classes = OpenTelemetry.class)
@Requires(beans = OpenTelemetry.class)
@Requires(property = GraphQLObservability.PREFIX + ".enabled", notEquals = "false")
@Requires(property = GraphQLObservability.PREFIX + ".tracing.enabled", notEquals = "false")
final class TracingObserver implements RequestObserver {

    static final String INSTRUMENTATION_NAME = "io.micronaut.graphql.tools";
    static final String SPAN_KEY = INSTRUMENTATION_NAME + ".request-span";

    private final Tracer tracer;

    TracingObserver(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    @Override
    public Object start(GraphQLContext context) {
        Span span = tracer.spanBuilder("graphql").setSpanKind(SpanKind.INTERNAL).startSpan();
        context.put(SPAN_KEY, span);
        return span;
    }

    @Override
    public void end(Object token, @Nullable String operationName, @Nullable String operationType, @Nullable ExecutionResult result,
                    @Nullable Throwable failure, GraphQLContext context) {
        Span span = (Span) token;
        if (operationType != null) {
            span.setAttribute(GraphQLObservability.OPERATION_TYPE_ATTRIBUTE, operationType);
            span.updateName(operationName == null ? operationType : operationType + " " + operationName);
        }
        if (operationName != null) {
            span.setAttribute(GraphQLObservability.OPERATION_NAME_ATTRIBUTE, operationName);
        }
        if (failure != null) {
            span.recordException(failure);
            span.setStatus(StatusCode.ERROR, failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
        } else if (result != null && !result.getErrors().isEmpty()) {
            span.setStatus(StatusCode.ERROR, result.getErrors().size() + (result.getErrors().size() == 1 ? " error" : " errors"));
        }
        span.end();
    }

}
