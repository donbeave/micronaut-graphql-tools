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
package io.micronaut.graphql.tools.observability;

/**
 * The names of the meters and tags that the module records. The attributes of its spans are the OpenTelemetry GraphQL
 * semantic-convention names, which the guide lists.
 */
public final class GraphQLObservability {

    /** The timer of one request, from the start of the execution to its result. */
    public static final String REQUEST_TIMER = "graphql.request";

    /**
     * The tag of {@link #REQUEST_TIMER} that holds the operation name. A client chooses the name, so the module keeps at most
     * {@code micronaut.metrics.binders.graphql.max-operations} distinct names; the rest are recorded as {@link #OTHER_OPERATION}. A request with no
     * operation name is recorded as {@link #ANONYMOUS_OPERATION}.
     */
    public static final String OPERATION_TAG = "operation";

    /** The tag of {@link #REQUEST_TIMER} that holds {@link #SUCCESS} or {@link #ERROR}. */
    public static final String OUTCOME_TAG = "outcome";

    /** The value of {@link #OUTCOME_TAG} of a request whose result has no error. */
    public static final String SUCCESS = "success";

    /** The value of {@link #OUTCOME_TAG} of a request that failed or whose result has an error. */
    public static final String ERROR = "error";

    /** The value of {@link #OPERATION_TAG} of a request that has no operation name. */
    public static final String ANONYMOUS_OPERATION = "anonymous";

    /** The value of {@link #OPERATION_TAG} of an operation name beyond the cap. */
    public static final String OTHER_OPERATION = "other";

    /**
     * The counter of the batches that the DataLoader of a batch loader bean dispatched. It exists only when
     * {@code micronaut.graphql.tools.execution.data-loader-statistics} is {@code true}.
     */
    public static final String DATA_LOADER_BATCHES = "graphql.dataloader.batches";

    /** The counter of the keys that those batches loaded. */
    public static final String DATA_LOADER_BATCH_KEYS = "graphql.dataloader.batch.keys";

    /** The tag of the DataLoader meters that holds the name of the batch loader bean. */
    public static final String LOADER_TAG = "loader";

    private GraphQLObservability() {
    }

}
