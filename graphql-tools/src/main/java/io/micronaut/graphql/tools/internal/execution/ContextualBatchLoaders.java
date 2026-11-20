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
package io.micronaut.graphql.tools.internal.execution;

import graphql.ExecutionInput;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Creates the {@code DataLoader} of a batch loader bean, with the batch function wrapped once so that it runs inside the
 * propagated context of the execution and, for a batch function that receives the {@code BatchLoaderEnvironment}, with the
 * {@code GraphQLContext} of the execution as its context. The wrapper installs the context only when the calling thread does not hold it
 * already (an identity check), so a call on the right thread allocates nothing.
 */
@Internal
final class ContextualBatchLoaders {

    private ContextualBatchLoaders() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static DataLoader<?, ?> create(DataLoaderTable.Kind kind, Object bean, ExecutionState state, @Nullable DataLoaderOptions options,
                                   @Nullable BatchCounters.Counter counter) {
        return switch (kind) {
            case BATCH -> DataLoaderFactory.newDataLoader((BatchLoaderWithContext) new Batch((BatchLoader) bean, state, counter), options);
            case BATCH_WITH_CONTEXT ->
                    DataLoaderFactory.newDataLoader((BatchLoaderWithContext) new BatchWithContext((BatchLoaderWithContext) bean, state, counter), options);
            case MAPPED -> DataLoaderFactory.newMappedDataLoader((MappedBatchLoaderWithContext) new Mapped((MappedBatchLoader) bean, state, counter), options);
            case MAPPED_WITH_CONTEXT -> DataLoaderFactory.newMappedDataLoader(
                    (MappedBatchLoaderWithContext) new MappedWithContext((MappedBatchLoaderWithContext) bean, state, counter), options);
        };
    }

    private static boolean installed(PropagatedContext context) {
        return PropagatedContext.getOrEmpty() == context;
    }

    private static BatchLoaderEnvironment withContext(BatchLoaderEnvironment environment, List<Object> keys, List<Object> keyContexts,
                                                      ExecutionState state) {
        if (environment.getContext() != null) {
            return environment;
        }
        ExecutionInput input = state.executionInput();
        if (input == null) {
            return environment;
        }
        return BatchLoaderEnvironment.newBatchLoaderEnvironment().context(input.getGraphQLContext()).keyContexts(keys, keyContexts).build();
    }

    private static BatchLoaderEnvironment withContext(BatchLoaderEnvironment environment, ExecutionState state) {
        if (environment.getContext() != null || state.executionInput() == null) {
            return environment;
        }
        Map<Object, Object> keyContexts = environment.getKeyContexts();
        return withContext(environment, new ArrayList<>(keyContexts.keySet()), new ArrayList<>(keyContexts.values()), state);
    }

    private record Batch(BatchLoader<Object, Object> delegate, ExecutionState state, @Nullable BatchCounters.Counter counter)
            implements BatchLoaderWithContext<Object, Object> {

        @Override
        public CompletionStage<List<Object>> load(List<Object> keys, BatchLoaderEnvironment environment) {
            if (counter != null) {
                counter.record(keys.size());
            }
            PropagatedContext context = state.propagatedContext();
            if (installed(context)) {
                return delegate.load(keys);
            }
            try (PropagatedContext.Scope ignored = context.propagate()) {
                return delegate.load(keys);
            }
        }
    }

    private record BatchWithContext(BatchLoaderWithContext<Object, Object> delegate, ExecutionState state, @Nullable BatchCounters.Counter counter)
            implements BatchLoaderWithContext<Object, Object> {

        @Override
        public CompletionStage<List<Object>> load(List<Object> keys, BatchLoaderEnvironment environment) {
            if (counter != null) {
                counter.record(keys.size());
            }
            BatchLoaderEnvironment seen = withContext(environment, keys, environment.getKeyContextsList(), state);
            PropagatedContext context = state.propagatedContext();
            if (installed(context)) {
                return delegate.load(keys, seen);
            }
            try (PropagatedContext.Scope ignored = context.propagate()) {
                return delegate.load(keys, seen);
            }
        }
    }

    private record Mapped(MappedBatchLoader<Object, Object> delegate, ExecutionState state, @Nullable BatchCounters.Counter counter)
            implements MappedBatchLoaderWithContext<Object, Object> {

        @Override
        public CompletionStage<Map<Object, Object>> load(Set<Object> keys, BatchLoaderEnvironment environment) {
            if (counter != null) {
                counter.record(keys.size());
            }
            PropagatedContext context = state.propagatedContext();
            if (installed(context)) {
                return delegate.load(keys);
            }
            try (PropagatedContext.Scope ignored = context.propagate()) {
                return delegate.load(keys);
            }
        }
    }

    private record MappedWithContext(MappedBatchLoaderWithContext<Object, Object> delegate, ExecutionState state, @Nullable BatchCounters.Counter counter)
            implements MappedBatchLoaderWithContext<Object, Object> {

        @Override
        public CompletionStage<Map<Object, Object>> load(Set<Object> keys, BatchLoaderEnvironment environment) {
            if (counter != null) {
                counter.record(keys.size());
            }
            BatchLoaderEnvironment seen = withContext(environment, state);
            PropagatedContext context = state.propagatedContext();
            if (installed(context)) {
                return delegate.load(keys, seen);
            }
            try (PropagatedContext.Scope ignored = context.propagate()) {
                return delegate.load(keys, seen);
            }
        }
    }

}
