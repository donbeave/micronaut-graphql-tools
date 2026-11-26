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
package io.micronaut.graphql.tools.security;

import graphql.GraphQLContext;
import graphql.language.OperationDefinition;
import io.micronaut.core.annotation.NonNull;

import java.util.List;

/**
 * Sees the root fields of an operation after the document is parsed and validated and before any field is fetched. It lets an
 * application decide on the fields an operation asks for without parsing the request body a second time, for example in an HTTP
 * filter.
 *
 * <p>Every bean of this type is called once for each operation, in the order of
 * {@link io.micronaut.core.annotation.Order @Order} on the bean class or of {@link io.micronaut.core.order.Ordered#getOrder()},
 * lowest first. The call is on the thread that starts the execution. The module registers its {@code Instrumentation} only
 * when at least one bean of this type exists, so an application without one pays nothing.</p>
 *
 * <p>A hook that rejects the operation throws {@link graphql.execution.AbortExecutionException}: its message is the error of
 * the response and no field is fetched.</p>
 */
@FunctionalInterface
public interface GraphQLRootFieldsHook {

    /**
     * @param operation  the kind of the operation
     * @param rootFields the names (not the aliases) of the root fields of the operation, in document order, without
     *                   duplicates, with the fields of fragments included; the directives {@code @skip} and {@code @include}
     *                   are not applied
     * @param context    the context of the execution, with what the {@code GraphQLContextContributor} beans put in it
     */
    void onRootFields(@NonNull OperationDefinition.Operation operation, @NonNull List<String> rootFields,
                      @NonNull GraphQLContext context);

}
