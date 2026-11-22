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

/**
 * Adds values to the {@link GraphQLContext} of an execution, where resolvers read them with
 * {@code DataFetchingEnvironment#getGraphQLContext()}.
 *
 * <p>Every bean of this type is called once for each execution (one operation, also on a WebSocket connection), in the
 * order of {@link io.micronaut.core.annotation.Order @Order} on the bean class or of
 * {@link io.micronaut.core.order.Ordered#getOrder()}, lowest first. The call is on the execution executor
 * (<code>micronaut.graphql.tools.execution.executor</code>), after the {@code GraphQLExecutionInputCustomizer}, inside the
 * propagated context of the request, so a contributor may block and may use request-scoped beans. When no bean exists,
 * the library does nothing here.</p>
 *
 * <p>A contributor that rejects the operation throws a {@link graphql.GraphQLError} (for example
 * {@link graphql.GraphqlErrorException}): its error is the only error of the response and no resolver runs. Any other
 * exception is logged at ERROR and the response carries a generic error that does not leak its message.</p>
 *
 * <p>{@code request.getExecutionInput().getDataLoaderRegistry()} is a registry of this execution that a contributor may
 * register loaders in. With batch loader beans it is the library's registry, which also creates the bean loaders on first
 * use. Without them, and with at least one contributor, it is a new empty registry for each execution. Without loader beans
 * and without contributors no registry is set, and graphql-java keeps its shared empty one.</p>
 */
@FunctionalInterface
public interface GraphQLContextContributor {

    /**
     * Adds values to the context of one execution.
     *
     * @param context the context of the execution
     * @param request a read-only view of the execution
     */
    void contribute(GraphQLContext context, GraphQLExecutionRequest request);

}
