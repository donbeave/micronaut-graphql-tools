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

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The batch counters of one execution, one {@link Counter} for each loader that the execution created.
 */
@Internal
final class BatchCounters implements DataLoaderBatchStatistics {

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    Counter counter(String loader) {
        return counters.computeIfAbsent(loader, name -> new Counter());
    }

    @Override
    public Map<String, Counts> counts() {
        Map<String, Counts> result = new HashMap<>();
        counters.forEach((name, counter) -> {
            long batches = counter.batches.get();
            if (batches > 0) {
                result.put(name, new Counts(batches, counter.keys.get()));
            }
        });
        return result;
    }

    static final class Counter {

        private final AtomicLong batches = new AtomicLong();
        private final AtomicLong keys = new AtomicLong();

        void record(int batchKeys) {
            batches.incrementAndGet();
            keys.addAndGet(batchKeys);
        }
    }

}
