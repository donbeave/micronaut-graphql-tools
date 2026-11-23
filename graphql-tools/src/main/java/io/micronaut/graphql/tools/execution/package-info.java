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
/**
 * The per-execution seam of the library's invocation: the beans that add values to the GraphQL context of each operation, the beans that change the options of one batch loader, the beans that decorate the fetchers of selected fields
 * and the read-only view of the operation that they receive. Key types: {@link io.micronaut.graphql.tools.execution.GraphQLContextContributor}, {@link io.micronaut.graphql.tools.execution.GraphQLExecutionRequest}, {@link io.micronaut.graphql.tools.execution.GraphQLTransport}, {@link io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics}, {@link io.micronaut.graphql.tools.execution.DataLoaderOptionsCustomizer}, {@link io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator}, {@link io.micronaut.graphql.tools.execution.FieldDescriptor}.
 *
 * <p><b>Audience.</b> Public API. An application implements {@code GraphQLContextContributor} or {@code GraphQLFieldFetcherDecorator} as a bean and reads the request view.</p>
 */
package io.micronaut.graphql.tools.execution;
