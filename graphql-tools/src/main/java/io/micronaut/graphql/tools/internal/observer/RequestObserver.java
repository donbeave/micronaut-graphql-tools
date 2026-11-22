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

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Observes one request: {@link #start} when the execution begins, {@link #operationStarted} when its operation is known and
 * about to run, {@link #dispatched} when the synchronous part of the operation is over, and {@link #end} with the result. The
 * token that {@code start} returns goes back to every later call, so that an observer keeps no per-request state of its own.
 */
@Internal
public interface RequestObserver {

    /**
     * @param context the GraphQL context of the execution
     * @return the token of this request, passed back to the later calls
     */
    Object start(GraphQLContext context);

    /**
     * Called on the thread that runs the operation, after parsing and validation and before the first field. The
     * observer may install something for the thread here; it removes it in {@link #dispatched}, on the same thread.
     *
     * @param token         what {@link #start} returned
     * @param context       the GraphQL context of the execution
     * @param operationName the name of the operation, or null for an anonymous one
     * @param operationType {@code query}, {@code mutation} or {@code subscription}
     */
    default void operationStarted(Object token, GraphQLContext context, @Nullable String operationName, String operationType) {
    }

    /**
     * Called on the thread of {@link #operationStarted}, when the operation returned its future: the fields that wait for a value
     * continue later, on whichever thread completes them.
     *
     * @param token what {@link #start} returned
     */
    default void dispatched(Object token) {
    }

    /**
     * @param token         what {@link #start} returned
     * @param operationName the name of the operation, or null for an anonymous one
     * @param operationType {@code query}, {@code mutation} or {@code subscription}, or null when the operation was never reached
     * @param result        the result, null when the execution failed
     * @param failure       the failure, null when there is a result
     * @param context       the GraphQL context of the execution
     */
    void end(Object token, @Nullable String operationName, @Nullable String operationType, @Nullable ExecutionResult result,
             @Nullable Throwable failure, GraphQLContext context);

}
