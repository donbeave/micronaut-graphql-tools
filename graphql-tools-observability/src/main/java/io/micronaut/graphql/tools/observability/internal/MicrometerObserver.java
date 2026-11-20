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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics;
import io.micronaut.graphql.tools.observability.GraphQLObservability;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Records the request timer and the DataLoader batch counters in the application's {@link MeterRegistry}.
 */
@Internal
@Singleton
@Requires(classes = MeterRegistry.class)
@Requires(beans = MeterRegistry.class)
@Requires(property = GraphQLObservability.PREFIX + ".enabled", notEquals = "false")
@Requires(property = GraphQLObservability.PREFIX + ".metrics.enabled", notEquals = "false")
final class MicrometerObserver implements RequestObserver {

    private final MeterRegistry registry;
    private final OperationNames operations;

    MicrometerObserver(MeterRegistry registry, ObservabilitySettings settings) {
        this.registry = registry;
        this.operations = new OperationNames(settings.getMetrics().getMaxOperations());
    }

    @Override
    public Object start(GraphQLContext context) {
        return Timer.start(registry);
    }

    @Override
    public void end(Object token, @Nullable String operationName, @Nullable String operationType, @Nullable ExecutionResult result,
                    @Nullable Throwable failure, GraphQLContext context) {
        boolean failed = failure != null || result == null || !result.getErrors().isEmpty();
        ((Timer.Sample) token).stop(registry.timer(GraphQLObservability.REQUEST_TIMER,
                GraphQLObservability.OPERATION_TAG, operations.tag(operationName),
                GraphQLObservability.OUTCOME_TAG, failed ? GraphQLObservability.ERROR : GraphQLObservability.SUCCESS));
        DataLoaderBatchStatistics batches = DataLoaderBatchStatistics.of(context);
        if (batches != null) {
            for (Map.Entry<String, DataLoaderBatchStatistics.Counts> entry : batches.counts().entrySet()) {
                count(GraphQLObservability.DATA_LOADER_BATCHES, entry.getKey(), entry.getValue().batches());
                count(GraphQLObservability.DATA_LOADER_BATCH_KEYS, entry.getKey(), entry.getValue().keys());
            }
        }
    }

    private void count(String meter, String loader, long amount) {
        Counter.builder(meter).tag(GraphQLObservability.LOADER_TAG, loader).register(registry).increment(amount);
    }

}
