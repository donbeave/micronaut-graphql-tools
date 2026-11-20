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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
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

/**
 * The one {@code Instrumentation} bean of the module. It implements the three request-level hooks and nothing at field level,
 * so an execution with it installed pays nothing per field: graphql-java gets the no-op field hooks of
 * {@link SimplePerformantInstrumentation}. The metrics and the tracing are observers behind it, so that the module adds one
 * instrumentation to the application's chain however many of them are on.
 */
@Internal
@Singleton
@Requires(beans = RequestObserver.class)
public final class ObservabilityInstrumentation extends SimplePerformantInstrumentation {

    private final RequestObserver[] observers;

    ObservabilityInstrumentation(List<RequestObserver> observers) {
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
        return SimpleInstrumentationContext.noOp();
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
