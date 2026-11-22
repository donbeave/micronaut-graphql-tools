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
package io.micronaut.graphql.tools.micrometer.internal;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics;
import io.micronaut.graphql.tools.internal.observer.RequestObserver;
import io.micronaut.graphql.tools.micrometer.GraphQLMetrics;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Records the request timer and the DataLoader batch counters in the application's {@link MeterRegistry}.
 */
@Internal
@Singleton
@RequiresMetrics
@Requires(classes = MeterRegistry.class)
@Requires(property = GraphQLMetricsConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
final class MicrometerObserver implements RequestObserver {

    private final MeterRegistry registry;
    private final OperationNames operations;

    MicrometerObserver(MeterRegistry registry, GraphQLMetricsConfiguration configuration) {
        this.registry = registry;
        this.operations = new OperationNames(configuration.getMaxOperations());
    }

    @Override
    public Object start(GraphQLContext context) {
        return Timer.start(registry);
    }

    @Override
    public void end(Object token, @Nullable String operationName, @Nullable String operationType, @Nullable ExecutionResult result,
                    @Nullable Throwable failure, GraphQLContext context) {
        boolean failed = failure != null || result == null || !result.getErrors().isEmpty();
        ((Timer.Sample) token).stop(registry.timer(GraphQLMetrics.REQUEST_TIMER,
                GraphQLMetrics.OPERATION_TAG, operations.tag(operationName),
                GraphQLMetrics.OUTCOME_TAG, failed ? GraphQLMetrics.ERROR : GraphQLMetrics.SUCCESS));
        DataLoaderBatchStatistics batches = DataLoaderBatchStatistics.of(context);
        if (batches != null) {
            for (Map.Entry<String, DataLoaderBatchStatistics.Counts> entry : batches.counts().entrySet()) {
                count(GraphQLMetrics.DATA_LOADER_BATCHES, entry.getKey(), entry.getValue().batches());
                count(GraphQLMetrics.DATA_LOADER_BATCH_KEYS, entry.getKey(), entry.getValue().keys());
            }
        }
    }

    private void count(String meter, String loader, long amount) {
        Counter.builder(meter).tag(GraphQLMetrics.LOADER_TAG, loader).register(registry).increment(amount);
    }

}
