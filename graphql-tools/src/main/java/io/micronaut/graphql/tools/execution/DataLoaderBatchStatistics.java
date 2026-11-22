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
package io.micronaut.graphql.tools.execution;

import graphql.GraphQLContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Map;

/**
 * The batches that the {@code DataLoader}s of one execution dispatched, counted for each batch loader bean. The library
 * counts them only when {@code micronaut.graphql.tools.execution.data-loader-statistics} is {@code true}: the counting adds
 * two atomic increments to each batch, and nothing to an execution without a batch loader or with the key off.
 */
public interface DataLoaderBatchStatistics {

    /** The key of the GraphQL context that holds the statistics of an execution. */
    String CONTEXT_KEY = "io.micronaut.graphql.tools.data-loader-batch-statistics";

    /**
     * @param context the GraphQL context of an execution
     * @return the statistics of that execution, or null when they are off or the library's invocation did not start it
     */
    @Nullable
    static DataLoaderBatchStatistics of(@NonNull GraphQLContext context) {
        return context.get(CONTEXT_KEY);
    }

    /**
     * @return the counts so far, by the name of the batch loader bean; a loader that dispatched no batch is not listed
     */
    @NonNull
    Map<String, Counts> getCounts();

    /**
     * @param batches how many batches the loader dispatched
     * @param keys    how many keys those batches loaded
     */
    record Counts(long batches, long keys) {
    }

}
