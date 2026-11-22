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
package io.micronaut.graphql.tools.internal.observer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import io.micronaut.context.annotation.Requires;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.OperationDefinition;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The one {@code Instrumentation} bean that the library adds for observers. It exists only when an application has a
 * {@link RequestObserver} bean, which the metrics and the tracing modules contribute. It implements the request-level hooks and
 * nothing at field level, so an execution with it installed pays nothing per field: graphql-java gets the no-op field hooks of
 * {@link SimplePerformantInstrumentation}. However many observers are on, the library adds one instrumentation to the chain.
 */
@Internal
@Singleton
@Requires(beans = RequestObserver.class)
public final class ObserverInstrumentation extends SimplePerformantInstrumentation {

    private final RequestObserver[] observers;

    ObserverInstrumentation(List<RequestObserver> observers) {
        this.observers = observers.toArray(new RequestObserver[0]);
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new RequestState(observers.length);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
                                                                  InstrumentationState state) {
        RequestState request = (RequestState) state;
        for (int i = 0; i < observers.length; i++) {
            request.tokens[i] = observers[i].start(parameters.getGraphQLContext());
        }
        ExecutionInput input = parameters.getExecutionInput();
        return SimpleInstrumentationContext.whenCompleted((result, failure) -> {
            String name = request.operationName != null ? request.operationName : input.getOperationName();
            for (int i = 0; i < observers.length; i++) {
                observers[i].end(request.tokens[i], name, request.operationType, result, failure, input.getGraphQLContext());
            }
        });
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters,
                                                                         InstrumentationState state) {
        OperationDefinition operation = parameters.getExecutionContext().getOperationDefinition();
        RequestState request = (RequestState) state;
        request.operationName = operation.getName();
        request.operationType = operation.getOperation().name().toLowerCase(Locale.ROOT);
        GraphQLContext context = parameters.getExecutionContext().getGraphQLContext();
        for (int i = 0; i < observers.length; i++) {
            observers[i].operationStarted(request.tokens[i], context, request.operationName, request.operationType);
        }
        return new Dispatch(request);
    }

    /**
     * Tells the observers that the operation returned its future. graphql-java 21 calls {@code onDispatched(CompletableFuture)}
     * and 22 and later call {@code onDispatched()}: the class has both, and neither is an override of the version it compiles
     * against.
     */
    private final class Dispatch implements InstrumentationContext<ExecutionResult> {

        private final RequestState request;

        private Dispatch(RequestState request) {
            this.request = request;
        }

        public void onDispatched(CompletableFuture<ExecutionResult> result) {
            onDispatched();
        }

        public void onDispatched() {
            for (int i = observers.length - 1; i >= 0; i--) {
                observers[i].dispatched(request.tokens[i]);
            }
        }

        @Override
        public void onCompleted(ExecutionResult result, Throwable failure) {
        }
    }

    private static final class RequestState implements InstrumentationState {

        private final Object[] tokens;
        private volatile String operationName;
        private volatile String operationType;

        private RequestState(int observers) {
            this.tokens = new Object[observers];
        }
    }

}
