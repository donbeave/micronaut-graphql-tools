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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.execution.DataLoaderOptionsCustomizer;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The {@code DataLoaderRegistry} of one execution. It holds no loader when it is created: a loader is created from the
 * startup table on first use, by name (the {@code DataFetchingEnvironment#getDataLoader} path) or by index (a typed
 * {@code DataLoader} parameter, which does no string lookup), and is then registered in the map of the registry, so that
 * the dispatch and the list of loaders see only loaders that were created. A registered but unused loader costs nothing.
 *
 * <p>The index lookup reads an immutable snapshot of the loaders created so far. A loader is created under a short
 * monitor that holds no I/O and never calls a batch function, so two threads never create two loaders of one name.</p>
 */
@Internal
public final class LazyDataLoaderRegistry extends DataLoaderRegistry {

    private static final Created NONE = new Created(new int[0], new DataLoader<?, ?>[0]);

    private final DataLoaderTable table;
    private final ExecutionState state;
    @Nullable
    private final BatchCounters counters;
    private volatile Created created = NONE;

    LazyDataLoaderRegistry(DataLoaderTable table, ExecutionState state, @Nullable BatchCounters counters) {
        this.table = table;
        this.state = state;
        this.counters = counters;
    }

    /**
     * The loader of a table entry, created on first use.
     *
     * @param index the index of the loader in the table
     * @return the loader of this execution
     */
    public DataLoader<?, ?> loader(int index) {
        Created snapshot = created;
        int[] indexes = snapshot.indexes;
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] == index) {
                return snapshot.loaders[i];
            }
        }
        return create(index);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> DataLoader<K, V> getDataLoader(String key) {
        DataLoader<K, V> registered = super.getDataLoader(key);
        if (registered != null) {
            return registered;
        }
        DataLoaderTable.Entry entry = table.entry(key);
        return entry == null ? null : (DataLoader<K, V>) loader(entry.index());
    }

    @Override
    public <K, V> DataLoader<K, V> computeIfAbsent(String key, Function<String, DataLoader<?, ?>> mappingFunction) {
        DataLoader<K, V> existing = getDataLoader(key);
        return existing != null ? existing : super.computeIfAbsent(key, mappingFunction);
    }

    private DataLoader<?, ?> create(int index) {
        DataLoaderTable.Entry entry = table.entry(index);
        // the bean is looked up outside the monitor: a first use of a lazy singleton may take a while
        Object bean = table.beanContext().getBean(entry.definition());
        synchronized (this) {
            Created snapshot = created;
            for (int i = 0; i < snapshot.indexes.length; i++) {
                if (snapshot.indexes[i] == index) {
                    return snapshot.loaders[i];
                }
            }
            DataLoader<?, ?> registered = super.getDataLoader(entry.name());
            DataLoader<?, ?> loader = registered != null ? registered
                    : ContextualBatchLoaders.create(entry.kind(), bean, state, options(entry),
                            counters == null ? null : counters.counter(entry.name()));
            int size = snapshot.indexes.length;
            int[] indexes = Arrays.copyOf(snapshot.indexes, size + 1);
            DataLoader<?, ?>[] loaders = Arrays.copyOf(snapshot.loaders, size + 1);
            indexes[size] = index;
            loaders[size] = loader;
            if (registered == null) {
                dataLoaders.put(entry.name(), loader);
            }
            created = new Created(indexes, loaders);
            return loader;
        }
    }

    @Nullable
    private DataLoaderOptions options(DataLoaderTable.Entry entry) {
        DataLoaderOptions options = state.isSubscription() ? new UncachedOptions() : null;
        for (DataLoaderOptionsCustomizer customizer : entry.customizers()) {
            DataLoaderOptions customized = customizer.customize(options == null ? new DataLoaderOptions() : options);
            if (customized == null) {
                throw new IllegalStateException("The DataLoaderOptionsCustomizer " + customizer.getClass().getName()
                        + " returned no options for the loader '" + entry.name() + "'");
            }
            options = customized;
        }
        return options;
    }

    /**
     * The options of a loader of a subscription operation: the same as the defaults, with caching off. Only a getter is
     * overridden, because the setters of {@code DataLoaderOptions} differ between java-dataloader 3.x and 6.x.
     */
    private static final class UncachedOptions extends DataLoaderOptions {

        @Override
        public boolean cachingEnabled() {
            return false;
        }
    }

    private record Created(int[] indexes, DataLoader<?, ?>[] loaders) {
    }

}
