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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Observes one request: {@link #start} when the execution begins and {@link #end} with its result. The token that
 * {@code start} returns goes back to {@code end}, so that an observer keeps no per-request state of its own.
 */
@Internal
interface RequestObserver {

    /**
     * @param context the GraphQL context of the execution
     * @return the token of this request, passed back to {@link #end}
     */
    Object start(GraphQLContext context);

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
