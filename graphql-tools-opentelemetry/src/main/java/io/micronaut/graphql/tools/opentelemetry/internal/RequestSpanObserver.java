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
package io.micronaut.graphql.tools.opentelemetry.internal;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.graphql.tools.internal.observer.RequestObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Singleton;

/**
 * Starts one span for each request with an OpenTelemetry {@link Instrumenter}, a child of the span that is current when the
 * execution begins (the HTTP server span, when the application has the HTTP integration of micronaut-tracing), and ends it
 * with the result. The span is named like the OpenTelemetry GraphQL semantic conventions say: the operation type, plus the
 * operation name when {@code micronaut.graphql.tools.tracing.add-operation-name-to-span-name} is true. It is current on the
 * thread that runs the operation and in the propagated context of the execution, so that a span started inside a resolver is
 * its child, and the spans of the selected fields are its children.
 */
@Internal
@Singleton
@Requires(classes = {OpenTelemetry.class, Instrumenter.class})
@Requires(beans = OpenTelemetry.class)
@Requires(property = "micronaut.otel.enabled", notEquals = StringUtils.FALSE)
@Requires(property = GraphQLTracingConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
final class RequestSpanObserver implements RequestObserver {

    static final String INSTRUMENTATION_NAME = "io.micronaut.graphql.tools";
    static final String CONTEXT_KEY = INSTRUMENTATION_NAME + ".request-span-context";
    static final String FALLBACK_NAME = "GraphQL Operation";

    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
    private static final AttributeKey<String> EXCEPTION_MESSAGE = AttributeKey.stringKey("exception.message");

    private final Instrumenter<OperationRequest, ExecutionResult> instrumenter;
    private final boolean addOperationName;

    RequestSpanObserver(Instrumenter<OperationRequest, ExecutionResult> instrumenter, GraphQLTracingConfiguration configuration) {
        this.addOperationName = configuration.isAddOperationNameToSpanName();
        this.instrumenter = instrumenter;
    }

    @Override
    public Object start(GraphQLContext context) {
        OperationRequest request = new OperationRequest();
        Context parent = Context.current();
        if (!instrumenter.shouldStart(parent, request)) {
            return new RequestSpan(request, null);
        }
        Context started = instrumenter.start(parent, request);
        context.put(CONTEXT_KEY, started);
        return new RequestSpan(request, started);
    }

    @Override
    public void operationStarted(Object token, GraphQLContext context, @Nullable String operationName, String operationType) {
        RequestSpan span = (RequestSpan) token;
        if (span.context == null) {
            return;
        }
        span.request.operation(operationName, operationType);
        Span.fromContext(span.context).updateName(addOperationName && operationName != null
                ? operationType + " " + operationName : operationType);
        SpanPropagatedContext element = new SpanPropagatedContext(span.context);
        ExecutionState state = ExecutionState.of(context);
        PropagatedContext propagated = state != null ? state.addToPropagatedContext(element)
                : PropagatedContext.getOrEmpty().plus(element);
        span.scope = propagated.propagate();
    }

    @Override
    public void dispatched(Object token) {
        RequestSpan span = (RequestSpan) token;
        PropagatedContext.Scope scope = span.scope;
        if (scope != null) {
            span.scope = null;
            scope.close();
        }
    }

    @Override
    public void end(Object token, @Nullable String operationName, @Nullable String operationType, @Nullable ExecutionResult result,
                    @Nullable Throwable failure, GraphQLContext context) {
        RequestSpan span = (RequestSpan) token;
        if (span.context == null) {
            return;
        }
        span.request.operation(operationName, operationType);
        if (result != null && !result.getErrors().isEmpty()) {
            Span current = Span.fromContext(span.context);
            for (GraphQLError error : result.getErrors()) {
                current.addEvent("exception", Attributes.of(EXCEPTION_TYPE, String.valueOf(error.getErrorType()),
                        EXCEPTION_MESSAGE, String.valueOf(error.getMessage())));
            }
        }
        instrumenter.end(span.context, span.request, result, failure);
    }

    /** The span of one request, and the scope that makes it current while the operation is dispatched. */
    private static final class RequestSpan {

        private final OperationRequest request;
        private final Context context;
        private PropagatedContext.Scope scope;

        private RequestSpan(OperationRequest request, @Nullable Context context) {
            this.request = request;
            this.context = context;
        }
    }

}
